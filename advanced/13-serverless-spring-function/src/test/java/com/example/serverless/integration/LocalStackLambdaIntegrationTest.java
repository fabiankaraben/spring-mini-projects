package com.example.serverless.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.Environment;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.lambda.model.State;

import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.IAM;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.LAMBDA;

/**
 * Full integration test using Testcontainers + LocalStack to simulate AWS Lambda.
 *
 * <p>This test demonstrates the complete "serverless" path:
 * <ol>
 *   <li>Build the fat-jar (done by Maven before tests run via the build lifecycle).</li>
 *   <li>Start a LocalStack container (simulates AWS Lambda + IAM locally).</li>
 *   <li>Create a fake IAM role (LocalStack does not validate policies).</li>
 *   <li>Deploy the fat-jar as a Lambda function via the AWS SDK v2 Lambda client.</li>
 *   <li>Invoke the Lambda function and assert the response JSON.</li>
 * </ol>
 *
 * <p><strong>Why LocalStack?</strong>
 * LocalStack is an open-source AWS cloud service emulator. It runs in Docker and
 * provides a real Lambda execution environment (using Docker-in-Docker or a
 * lightweight process runner) that is functionally equivalent to real AWS Lambda.
 * This lets us verify the deployment packaging and invocation path without
 * an AWS account or real AWS costs.
 *
 * <p><strong>Spring Cloud Function on Lambda</strong>
 * The function is deployed with the handler class
 * {@code org.springframework.cloud.function.adapter.aws.FunctionInvoker} —
 * the Spring Cloud Function AWS adapter entry point. The adapter:
 * <ul>
 *   <li>Starts the Spring Boot application context (cached across invocations).</li>
 *   <li>Routes the Lambda event payload to the function bean named in
 *       {@code SPRING_CLOUD_FUNCTION_DEFINITION}.</li>
 *   <li>Returns the function's output as the Lambda response.</li>
 * </ul>
 *
 * <p><strong>Note on JAR availability</strong>
 * This test reads the fat-jar from {@code target/*.jar}. Maven's Surefire plugin
 * runs tests after the {@code package} phase when using {@code mvn verify} or
 * {@code mvn test} (which implies compile + test-compile but NOT package). To
 * ensure the jar exists when tests run, we use a pre-built jar check and skip
 * gracefully if no jar is found (e.g., during unit-only runs with {@code -DskipTests}).
 *
 * <p><strong>Architecture note</strong>
 * The LocalStack Lambda execution environment runs the JVM inside the container.
 * This test uploads the fat-jar produced by {@code spring-boot-maven-plugin}
 * and verifies that the AWS Lambda adapter can route events to the correct
 * Spring Cloud Function bean.
 */
@Testcontainers
@DisplayName("LocalStack Lambda Integration Tests (Spring Cloud Function on simulated AWS Lambda)")
class LocalStackLambdaIntegrationTest {

    /**
     * LocalStack container using the official multi-arch image.
     *
     * <p>Services enabled:
     * <ul>
     *   <li>{@code LAMBDA} — the AWS Lambda simulator</li>
     *   <li>{@code IAM}    — needed to create a fake execution role</li>
     * </ul>
     *
     * <p>Declared {@code static} so the container is shared across all test methods,
     * avoiding the overhead of restarting LocalStack between tests.
     *
     * <p>The {@code withCopyFileToContainer} call pre-stages the fat-jar inside
     * the container's temp area for later Lambda function creation.
     */
    @Container
    static final LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.1"))
                    .withServices(LAMBDA, IAM)
                    // Increase startup timeout — LocalStack Lambda init takes time
                    .withStartupTimeout(Duration.ofMinutes(2));

    /** AWS SDK v2 Lambda client pointing to LocalStack. */
    private static LambdaClient lambdaClient;

    /** AWS SDK v2 IAM client pointing to LocalStack. */
    private static IamClient iamClient;

    /** Jackson ObjectMapper for JSON assertion helpers. */
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();  // registers JavaTimeModule for Instant support

    /**
     * The ARN of the fake IAM execution role created in LocalStack.
     * Lambda requires a role ARN even though LocalStack ignores permissions.
     */
    private static String lambdaRoleArn;

    /**
     * Path to the fat-jar produced by {@code spring-boot-maven-plugin}.
     * This jar contains the Spring Cloud Function AWS adapter and is
     * deployed to LocalStack Lambda as the function code.
     */
    private static File fatJar;

    /**
     * Indicates whether the fat-jar is available on disk.
     * If false, Lambda deployment tests are skipped.
     */
    private static boolean jarAvailable;

    /**
     * Sets up shared AWS SDK clients pointing to LocalStack and deploys
     * common Lambda infrastructure (IAM role) before any test runs.
     *
     * <p>{@code @BeforeAll} runs once per class, after Testcontainers starts
     * the LocalStack container.
     */
    @BeforeAll
    static void setUpAwsClients() throws Exception {
        // Build credentials and endpoint for all SDK clients.
        // LocalStack accepts any non-empty credentials.
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                        localstack.getAccessKey(),
                        localstack.getSecretKey()
                )
        );
        Region region = Region.of(localstack.getRegion());

        // Lambda client pointed at LocalStack's Lambda endpoint
        lambdaClient = LambdaClient.builder()
                .credentialsProvider(credentials)
                .region(region)
                .endpointOverride(localstack.getEndpointOverride(LAMBDA))
                .build();

        // IAM client pointed at LocalStack's IAM endpoint
        iamClient = IamClient.builder()
                .credentialsProvider(credentials)
                .region(Region.AWS_GLOBAL)  // IAM is global (no region)
                .endpointOverride(localstack.getEndpointOverride(IAM))
                .build();

        // Create a fake Lambda execution role.
        // LocalStack does not enforce IAM policies, but Lambda still requires a role ARN.
        String trustPolicy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"Service": "lambda.amazonaws.com"},
                    "Action": "sts:AssumeRole"
                  }]
                }
                """;
        CreateRoleResponse roleResponse = iamClient.createRole(CreateRoleRequest.builder()
                .roleName("lambda-execution-role")
                .assumeRolePolicyDocument(trustPolicy)
                .build());
        lambdaRoleArn = roleResponse.role().arn();

        // Locate the fat-jar in the Maven target directory.
        // Maven Surefire runs tests after compilation but the package phase
        // must have run to produce the jar (which happens in the full build).
        File targetDir = new File("target");
        fatJar = null;
        if (targetDir.exists()) {
            File[] jars = targetDir.listFiles(f ->
                    f.getName().endsWith(".jar") && !f.getName().endsWith("-sources.jar"));
            if (jars != null) {
                // Pick the largest jar — that is the Spring Boot fat-jar
                for (File jar : jars) {
                    if (fatJar == null || jar.length() > fatJar.length()) {
                        fatJar = jar;
                    }
                }
            }
        }
        jarAvailable = fatJar != null && fatJar.exists() && fatJar.length() > 1_000_000;
    }

    // =========================================================================
    // Lambda deployment helper
    // =========================================================================

    /**
     * Deploys the fat-jar to LocalStack Lambda as a new function with the
     * given name and configures Spring Cloud Function to invoke the specified
     * function bean.
     *
     * <p>The {@code SPRING_CLOUD_FUNCTION_DEFINITION} environment variable tells
     * the Spring Cloud Function AWS adapter which function bean to activate.
     *
     * @param functionName           AWS Lambda function name
     * @param springFunctionBeanName Spring bean name to invoke (e.g., "calculateTax")
     * @throws Exception if jar upload or function creation fails
     */
    private void deployFunction(String functionName, String springFunctionBeanName) throws Exception {
        SdkBytes jarBytes = SdkBytes.fromByteArray(
                java.nio.file.Files.readAllBytes(fatJar.toPath()));

        lambdaClient.createFunction(CreateFunctionRequest.builder()
                .functionName(functionName)
                .runtime(Runtime.JAVA21)
                // Spring Cloud Function AWS adapter entry point.
                // FunctionInvoker reads SPRING_CLOUD_FUNCTION_DEFINITION to select the bean.
                .handler("org.springframework.cloud.function.adapter.aws.FunctionInvoker")
                .role(lambdaRoleArn)
                .code(FunctionCode.builder()
                        .zipFile(jarBytes)
                        .build())
                .timeout(60)            // Lambda timeout in seconds
                .memorySize(512)        // MB of RAM for the JVM
                .environment(Environment.builder()
                        .variables(Map.of(
                                // Selects which Spring Cloud Function bean handles events
                                "SPRING_CLOUD_FUNCTION_DEFINITION", springFunctionBeanName,
                                // Disable ANSI colors in Lambda (no terminal)
                                "SPRING_OUTPUT_ANSI_ENABLED", "NEVER"
                        ))
                        .build())
                .build());

        // Wait for the function to become ACTIVE before invoking it.
        // Lambda may take a few seconds to initialize (JVM cold start).
        await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    FunctionConfiguration config = lambdaClient
                            .getFunction(r -> r.functionName(functionName))
                            .configuration();
                    assertThat(config.state())
                            .as("Lambda function %s should be ACTIVE", functionName)
                            .isEqualTo(State.ACTIVE);
                });
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Test
    @DisplayName("calculateTax function: deployed to LocalStack Lambda, invocation returns TaxResult")
    void calculateTaxFunctionDeployedToLambda() throws Exception {
        // Skip if fat-jar not built yet (e.g., running unit tests only)
        org.junit.jupiter.api.Assumptions.assumeTrue(
                jarAvailable,
                "Fat-jar not available in target/ — skipping Lambda deployment test. " +
                "Run 'mvn package -DskipTests' first, then re-run tests."
        );

        // Step 1: Deploy the fat-jar to LocalStack Lambda
        String functionName = "calculateTax-test";
        deployFunction(functionName, "calculateTax");

        // Step 2: Build the Lambda event payload
        // Spring Cloud Function AWS adapter accepts the function's input DTO directly as JSON.
        Map<String, Object> payload = Map.of(
                "orderId", "ORD-LAMBDA-001",
                "customerId", "CUST-LAMBDA-1",
                "subtotal", 100.00,
                "country", "US",
                "state", "CA"
        );
        String payloadJson = objectMapper.writeValueAsString(payload);

        // Step 3: Invoke the Lambda function
        InvokeResponse response = lambdaClient.invoke(InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromString(payloadJson, StandardCharsets.UTF_8))
                .build());

        // Step 4: Assert the response
        assertThat(response.functionError())
                .as("Lambda function should not have an error")
                .isNull();

        String responseJson = response.payload().asUtf8String();
        JsonNode result = objectMapper.readTree(responseJson);

        assertThat(result.get("orderId").asText()).isEqualTo("ORD-LAMBDA-001");
        // California: 8.75% tax on 100.00 = 8.75
        assertThat(new BigDecimal(result.get("taxAmount").asText()))
                .isEqualByComparingTo(new BigDecimal("8.75"));
        // total = 100.00 + 8.75 = 108.75
        assertThat(new BigDecimal(result.get("total").asText()))
                .isEqualByComparingTo(new BigDecimal("108.75"));
    }

    @Test
    @DisplayName("generateInvoice function: deployed to LocalStack Lambda, returns full Invoice")
    void generateInvoiceFunctionDeployedToLambda() throws Exception {
        // Skip if fat-jar not built yet
        org.junit.jupiter.api.Assumptions.assumeTrue(
                jarAvailable,
                "Fat-jar not available in target/ — skipping Lambda deployment test. " +
                "Run 'mvn package -DskipTests' first, then re-run tests."
        );

        // Deploy generateInvoice function to LocalStack
        String functionName = "generateInvoice-test";
        deployFunction(functionName, "generateInvoice");

        // Build payload: Germany order with SAVE10 discount
        // subtotal=200.00, tax=19% → taxAmount=38.00, totalBeforeDiscount=238.00
        // SAVE10 = 10% of 238.00 = 23.80, finalTotal = 214.20
        Map<String, Object> payload = Map.of(
                "orderId", "ORD-LAMBDA-002",
                "customerId", "CUST-LAMBDA-2",
                "subtotal", 200.00,
                "country", "DE",
                "discountCode", "SAVE10"
        );
        String payloadJson = objectMapper.writeValueAsString(payload);

        InvokeResponse response = lambdaClient.invoke(InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromString(payloadJson, StandardCharsets.UTF_8))
                .build());

        // Assert no Lambda error
        assertThat(response.functionError())
                .as("Lambda function should not have an error")
                .isNull();

        String responseJson = response.payload().asUtf8String();
        JsonNode invoice = objectMapper.readTree(responseJson);

        assertThat(invoice.get("orderId").asText()).isEqualTo("ORD-LAMBDA-002");
        assertThat(invoice.get("invoiceId").asText()).contains("ORD-LAMBDA-002");
        assertThat(new BigDecimal(invoice.get("taxAmount").asText()))
                .isEqualByComparingTo(new BigDecimal("38.00"));
        assertThat(new BigDecimal(invoice.get("totalBeforeDiscount").asText()))
                .isEqualByComparingTo(new BigDecimal("238.00"));
        assertThat(new BigDecimal(invoice.get("discountAmount").asText()))
                .isEqualByComparingTo(new BigDecimal("23.80"));
        assertThat(new BigDecimal(invoice.get("finalTotal").asText()))
                .isEqualByComparingTo(new BigDecimal("214.20"));
    }
}
