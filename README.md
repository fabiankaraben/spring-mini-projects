# spring-mini-projects
Spring mini-projects, each one a new challenge.

<p align="center"><img src="https://fabiankaraben.github.io/mini-projects/imgs/spring.webp" alt="Featured Image"></p>

## Setup instructions
1. Install Java 21 or higher (LTS)
2. Install Docker and Docker Compose
3. Run `./mvnw clean install` in each project directory.

## Basic
1. **Hello World HTTP Server**  
   🔹 This is a backend in Spring Boot serving a single GET endpoint that returns a 'Hello World' message.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/01-hello-world-http-server)

2. **Static File Server**  
   🔹 This is a backend in Spring Boot serving static HTML, CSS, and JS files from a resources directory.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/02-static-file-server)

3. **JSON Response API**  
   🔹 This is a backend in Spring Boot providing a GET endpoint that returns a JSON object mapped from a POJO.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/03-json-response-api)

4. **Form Data Handler**  
   🔹 This is a backend in Spring Boot handling POST requests with form-urlencoded data and echoing values.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/04-form-data-handler)

5. **Query Parameter Parser**  
   🔹 This is a backend in Spring Boot parsing query parameters and validating them using Spring MVC.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/05-query-parameter-parser)

6. **Basic Router**  
   🔹 This is a backend in Spring Boot implementing multiple mapping annotations (@GetMapping, @PostMapping, etc).  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/06-basic-router)

7. **Environment Variable Config**  
   🔹 This is a backend in Spring Boot reading environment variables using @Value and Environment.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/07-environment-variable-config)

8. **File Upload Server**  
   🔹 This is a backend in Spring Boot handling multipart/form-data requests to save files to local disk.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/08-file-upload-server)

9. **Cookie Setter and Reader**  
   🔹 This is a backend in Spring Boot setting a cookie on response and reading it using @CookieValue.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/09-cookie-setter-and-reader)

10. **Basic Error Handling**  
   🔹 This is a backend in Spring Boot using @ControllerAdvice to return standard RFC 7807 error responses.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/10-basic-error-handling)

11. **Simple Logger Middleware**  
   🔹 This is a backend in Spring Boot using a HandlerInterceptor to log request method, path, and duration.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/11-simple-logger-middleware)

12. **JSON Request Parser**  
   🔹 This is a backend in Spring Boot accepting a POST JSON body and mapping it with @RequestBody.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/12-json-request-parser)

13. **Content Negotiation**  
   🔹 This is a backend in Spring Boot returning JSON or XML based on the Accept header using HttpMessageConverters.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/13-content-negotiation)

14. **Custom Validator**  
   🔹 This is a backend in Spring Boot building a custom Bean Validation annotation for specific business rules.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/14-custom-validator)

15. **Profile Specific Config**  
   🔹 This is a backend in Spring Boot using @Profile to load different Spring beans based on environment.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/15-profile-specific-config)

16. **Actuator Info Endpoint**  
   🔹 This is a backend in Spring Boot exposing standard and custom info metrics via Spring Boot Actuator.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/16-actuator-info-endpoint)

17. **Simple Scheduled Task**  
   🔹 This is a backend in Spring Boot using @Scheduled to run a background job every few seconds.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/17-simple-scheduled-task)

18. **Async Method Execution**  
   🔹 This is a backend in Spring Boot using @Async to execute long-running methods in a separate thread pool.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/18-async-method-execution)

19. **Application Event Publisher**  
   🔹 This is a backend in Spring Boot publishing and listening to custom events within the ApplicationContext.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/19-application-event-publisher)

20. **Properties Configuration**  
   🔹 This is a backend in Spring Boot binding application.properties to a POJO with @ConfigurationProperties.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/20-properties-configuration)

21. **CommandLineRunner Init**  
   🔹 This is a backend in Spring Boot using CommandLineRunner to seed data or run logic on startup.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/21-commandlinerunner-init)

22. **Basic AOP Logging**  
   🔹 This is a backend in Spring Boot using AspectJ annotations to log method entry and exit in the service layer.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/22-basic-aop-logging)

23. **RestTemplate Client**  
   🔹 This is a backend in Spring Boot making basic HTTP GET and POST requests to external APIs using RestTemplate.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/23-resttemplate-client)

24. **WebClient Basic**  
   🔹 This is a backend in Spring Boot using the reactive WebClient to consume a public JSON API synchronously.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/24-webclient-basic)

25. **H2 Database Setup**  
   🔹 This is a backend in Spring Boot configuring an embedded H2 database and querying it on startup.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/25-h2-database-setup)

26. **JdbcTemplate CRUD**  
   🔹 This is a backend in Spring Boot performing basic SQL operations using Spring's JdbcTemplate.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/26-jdbctemplate-crud)

27. **Spring Data JPA Setup**  
   🔹 This is a backend in Spring Boot creating a simple Entity and connecting it to a basic Repository.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/27-spring-data-jpa-setup)

28. **JPA Custom Queries**  
   🔹 This is a backend in Spring Boot using @Query to define custom JPQL and native SQL queries.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/28-jpa-custom-queries)

29. **JPA Pagination**  
   🔹 This is a backend in Spring Boot retrieving database records in pages using Pageable and Spring Data.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/29-jpa-pagination)

30. **JPA Derived Queries**  
   🔹 This is a backend in Spring Boot creating repository methods by simply defining method names.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/30-jpa-derived-queries)

31. **Flyway Migrations**  
   🔹 This is a backend in Spring Boot managing database schema changes automatically using Flyway.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/31-flyway-migrations)

32. **Liquibase Migrations**  
   🔹 This is a backend in Spring Boot managing database schema changes automatically using Liquibase.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/32-liquibase-migrations)

33. **Basic Caching**  
   🔹 This is a backend in Spring Boot using @Cacheable to store method results in a simple in-memory ConcurrentHashMap.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/33-basic-caching)

34. **Thymeleaf Basic UI**  
   🔹 This is a backend in Spring Boot rendering a simple dynamic HTML page using Thymeleaf templates.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/34-thymeleaf-basic-ui)

35. **Freemarker Templates**  
   🔹 This is a backend in Spring Boot rendering a dynamic HTML page using Freemarker templates.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/35-freemarker-templates)

36. **Mustache Templates**  
   🔹 This is a backend in Spring Boot rendering a dynamic HTML page using Mustache templates.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/36-mustache-templates)

37. **Session Management**  
   🔹 This is a backend in Spring Boot storing user specific data across requests in HttpSession.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/37-session-management)

38. **CORS Global Config**  
   🔹 This is a backend in Spring Boot configuring Cross-Origin Resource Sharing globally for API endpoints.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/38-cors-global-config)

39. **I18n Messages**  
   🔹 This is a backend in Spring Boot using MessageSource to resolve localized messages based on Accept-Language.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/39-i18n-messages)

40. **Spring Boot DevTools**  
   🔹 This is a backend in Spring Boot showcasing auto-restart and LiveReload using DevTools.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/40-spring-boot-devtools)

41. **Custom Startup Banner**  
   🔹 This is a backend in Spring Boot displaying a custom ASCII graphic on application startup.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/41-custom-startup-banner)

42. **Configuration Server**  
   🔹 This is a backend in Spring Boot serving external application properties using Spring Cloud Config.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/42-configuration-server)

43. **Simple Filter**  
   🔹 This is a backend in Spring Boot creating a standard javax.servlet.Filter for low-level request manipulation.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/43-simple-filter)

44. **Request Scoped Beans**  
   🔹 This is a backend in Spring Boot demonstrating the use of @RequestScope for storing per-request state.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/44-request-scoped-beans)

45. **MockMvc Testing**  
   🔹 This is a backend in Spring Boot showing how to set up simple @WebMvcTest slices without the full context.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/45-mockmvc-testing)

46. **Custom ResponseBodyAdvice**  
   🔹 This is a backend in Spring Boot intercepting and modifying API responses before they are serialized.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/46-custom-responsebodyadvice)

47. **Jackson Custom Serializer**  
   🔹 This is a backend in Spring Boot creating custom Jackson serializers/deserializers for Spring Boot.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/47-jackson-custom-serializer)

48. **Hateoas Links**  
   🔹 This is a backend in Spring Boot adding hypermedia links to representations using Spring HATEOAS.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/48-hateoas-links)

49. **Data Rest Repositories**  
   🔹 This is a backend in Spring Boot exposing JPA repositories directly via Spring Data REST.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/49-data-rest-repositories)

50. **Exception Translators**  
   🔹 This is a backend in Spring Boot translating custom persistence exceptions to standard HTTP errors.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests using **JUnit 5** and **Mockito**. Sliced Integration Testing using `@WebMvcTest` or `@DataJpaTest`.  
   🔹 [Project directory](basic/50-exception-translators)

## Intermediate
1. **Basic Auth Security**  
   🔹 This is a backend in Spring Boot securing endpoints with Spring Security and HTTP Basic credentials.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/01-basic-auth-security)

2. **Form Login Security**  
   🔹 This is a backend in Spring Boot setting up a browser-based form login and logout flow.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/02-form-login-security)

3. **JWT Generation**  
   🔹 This is a backend in Spring Boot creating Json Web Tokens upon successful user login.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/03-jwt-generation)

4. **JWT Validation**  
   🔹 This is a backend in Spring Boot parsing and verifying JWTs in a custom Spring Security filter.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/04-jwt-validation)

5. **OAuth2 Login Client**  
   🔹 This is a backend in Spring Boot allowing users to log in via GitHub or Google using OAuth2.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/05-oauth2-login-client)

6. **Role Based Access**  
   🔹 This is a backend in Spring Boot using @PreAuthorize to restrict endpoints based on user roles.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/06-role-based-access)

7. **Method Level Security**  
   🔹 This is a backend in Spring Boot securing service layer methods using Spring Security annotations.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/07-method-level-security)

8. **Redis Data Cache**  
   🔹 This is a backend in Spring Boot caching method results in an external Redis instance via @Cacheable.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/08-redis-data-cache)

9. **Redis Session Store**  
   🔹 This is a backend in Spring Boot storing HTTP sessions in Redis using Spring Session.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/09-redis-session-store)

10. **MongoDB CRUD API**  
   🔹 This is a backend in Spring Boot performing basic NoSQL operations using Spring Data MongoDB.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/10-mongodb-crud-api)

11. **MongoDB Custom Queries**  
   🔹 This is a backend in Spring Boot using MongoTemplate for complex aggregation queries.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/11-mongodb-custom-queries)

12. **RabbitMQ Producer**  
   🔹 This is a backend in Spring Boot sending asynchronous messages to a RabbitMQ exchange.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/12-rabbitmq-producer)

13. **RabbitMQ Consumer**  
   🔹 This is a backend in Spring Boot listening for messages on a RabbitMQ queue with @RabbitListener.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/13-rabbitmq-consumer)

14. **Kafka Producer**  
   🔹 This is a backend in Spring Boot publishing events to an Apache Kafka topic using KafkaTemplate.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/14-kafka-producer)

15. **Kafka Consumer**  
   🔹 This is a backend in Spring Boot consuming events from a Kafka topic using @KafkaListener.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/15-kafka-consumer)

16. **WebSockets Chat**  
   🔹 This is a backend in Spring Boot broadcasting messages to connected clients using Spring WebSockets.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/16-websockets-chat)

17. **STOMP Over WebSockets**  
   🔹 This is a backend in Spring Boot implementing pub/sub message routing using STOMP protocols.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/17-stomp-over-websockets)

18. **GraphQL API**  
   🔹 This is a backend in Spring Boot exposing a data schema and resolving queries using Spring for GraphQL.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/18-graphql-api)

19. **GraphQL Mutations**  
   🔹 This is a backend in Spring Boot handling state changes through GraphQL mutations.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/19-graphql-mutations)

20. **Reactive WebFlux API**  
   🔹 This is a backend in Spring Boot building non-blocking endpoints using Spring WebFlux and Mono/Flux.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/20-reactive-webflux-api)

21. **Reactive R2DBC**  
   🔹 This is a backend in Spring Boot accessing a relational database asynchronously with Spring Data R2DBC.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/21-reactive-r2dbc)

22. **Reactive MongoDB API**  
   🔹 This is a backend in Spring Boot building a fully non-blocking stack with WebFlux and Reactive MongoDB.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/22-reactive-mongodb-api)

23. **Feign Client Integration**  
   🔹 This is a backend in Spring Boot creating declarative HTTP clients using OpenFeign.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/23-feign-client-integration)

24. **Circuit Breaker**  
   🔹 This is a backend in Spring Boot protecting failing services using Resilience4j CircuitBreaker.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/24-circuit-breaker)

25. **Retry and Rate Limiting**  
   🔹 This is a backend in Spring Boot applying Resilience4j Retry and RateLimiter to external calls.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/25-retry-and-rate-limiting)

26. **JavaMailSender Email**  
   🔹 This is a backend in Spring Boot sending plain text and HTML emails using JavaMailSender.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/26-javamailsender-email)

27. **Quartz Scheduler**  
   🔹 This is a backend in Spring Boot scheduling complex cron jobs using Quartz integrating with Spring.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/27-quartz-scheduler)

28. **Spring Batch CSV to DB**  
   🔹 This is a backend in Spring Boot reading a CSV file and storing valid rows into a database using Batch.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/28-spring-batch-csv-to-db)

29. **Spring Batch Schedulers**  
   🔹 This is a backend in Spring Boot triggering Spring Batch jobs periodically.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/29-spring-batch-schedulers)

30. **ActiveMQ JMS**  
   🔹 This is a backend in Spring Boot producing and consuming messages using Java Message Service and ActiveMQ.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/30-activemq-jms)

31. **Elasticsearch CRUD**  
   🔹 This is a backend in Spring Boot indexing and searching documents using Spring Data Elasticsearch.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/31-elasticsearch-crud)

32. **Neo4j Graph API**  
   🔹 This is a backend in Spring Boot modelling and querying nodes and relationships using Spring Data Neo4j.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/32-neo4j-graph-api)

33. **Cassandra Integration**  
   🔹 This is a backend in Spring Boot connecting to a wide-column store using Spring Data Cassandra.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/33-cassandra-integration)

34. **Vault Secrets**  
   🔹 This is a backend in Spring Boot storing and retrieving credentials securely using Spring Cloud Vault.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/34-vault-secrets)

35. **Docker Compose Support**  
   🔹 This is a backend in Spring Boot utilizing Spring Boot 3.1+ docker-compose integration for dev services.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/35-docker-compose-support)

36. **JPA Auditing**  
   🔹 This is a backend in Spring Boot automatically populating created and updated timestamps using @EntityListeners.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/36-jpa-auditing)

37. **Entity Lifecycle Events**  
   🔹 This is a backend in Spring Boot hooking into JPA PrePersist and PostLoad events for business logic.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/37-entity-lifecycle-events)

38. **Soft Delete Logic**  
   🔹 This is a backend in Spring Boot implementing logical deletions using @SQLDelete and @Where annotations.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/38-soft-delete-logic)

39. **Optimistic Locking**  
   🔹 This is a backend in Spring Boot preventing concurrent modification issues using JPA @Version.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/39-optimistic-locking)

40. **File Storage with S3**  
   🔹 This is a backend in Spring Boot uploading and downloading files to AWS S3 or MinIO compatibility API.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/40-file-storage-with-s3)

41. **Custom Auto-Configuration**  
   🔹 This is a backend in Spring Boot creating a custom Spring Boot Starter with auto-configured beans.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/41-custom-autoconfiguration)

42. **Validation Groups**  
   🔹 This is a backend in Spring Boot applying different bean validation rules based on sequence groups.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/42-validation-groups)

43. **Rate Limiting Filter**  
   🔹 This is a backend in Spring Boot implementing a simple token-bucket rate limiter via an interceptor.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/43-rate-limiting-filter)

44. **PDF Generation**  
   🔹 This is a backend in Spring Boot creating downloadable PDF documents using Spring and iText/OpenPDF.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/44-pdf-generation)

45. **Excel Export**  
   🔹 This is a backend in Spring Boot generating and downloading Excel files using Apache POI.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/45-excel-export)

46. **Barcode Generator**  
   🔹 This is a backend in Spring Boot generating QR codes and barcodes to return as image streams.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/46-barcode-generator)

47. **Stripe Payment API**  
   🔹 This is a backend in Spring Boot integrating with the Stripe Java SDK to process payments.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/47-stripe-payment-api)

48. **Twilio SMS Sender**  
   🔹 This is a backend in Spring Boot sending SMS text messages by integrating the Twilio API.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/48-twilio-sms-sender)

49. **Dynamic Scheduling**  
   🔹 This is a backend in Spring Boot modifying @Scheduled task frequencies at runtime dynamically.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/49-dynamic-scheduling)

50. **Testcontainers Postgres**  
   🔹 This is a backend in Spring Boot writing integration tests backed by a real Postgres docker container.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](intermediate/50-testcontainers-postgres)

## Advanced
1. **Eureka Discovery Server**  
   🔹 This is a backend in Spring Boot setting up a Spring Cloud Netflix Eureka service registry.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/01-eureka-discovery-server)

2. **Eureka Discovery Client**  
   🔹 This is a backend in Spring Boot registering a service with Eureka for dynamic discovery.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/02-eureka-discovery-client)

3. **Spring Cloud Gateway**  
   🔹 This is a backend in Spring Boot configuring an API Gateway to route requests to downstream microservices.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/03-spring-cloud-gateway)

4. **Gateway Rate Limiting**  
   🔹 This is a backend in Spring Boot applying Redis-based rate limiting filtering at the API Gateway.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/04-gateway-rate-limiting)

5. **Distributed Tracing Sleuth**  
   🔹 This is a backend in Spring Boot generating tracing IDs and spanning them across services with Micrometer.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/05-distributed-tracing-sleuth)

6. **Zipkin Server Integration**  
   🔹 This is a backend in Spring Boot exporting traces to a Zipkin server to visualize microservice latency.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/06-zipkin-server-integration)

7. **OAuth2 Authorization Server**  
   🔹 This is a backend in Spring Boot minting customized JWTs using Spring Authorization Server.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/07-oauth2-authorization-server)

8. **OAuth2 Resource Server**  
   🔹 This is a backend in Spring Boot validating OAuth2 tokens and securing APIs in a microservices pattern.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/08-oauth2-resource-server)

9. **Saga Pattern Choreography**  
   🔹 This is a backend in Spring Boot managing distributed transactions across microservices via events.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/09-saga-pattern-choreography)

10. **CQRS with Axon**  
   🔹 This is a backend in Spring Boot separating read and write models using the Axon Framework.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/10-cqrs-with-axon)

11. **Event Sourcing**  
   🔹 This is a backend in Spring Boot storing state as a sequence of events instead of current snapshots using Axon.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/11-event-sourcing)

12. **Spring Cloud Stream**  
   🔹 This is a backend in Spring Boot abstracting message brokers for event-driven microservices.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/12-spring-cloud-stream)

13. **Serverless Spring Function**  
   🔹 This is a backend in Spring Boot packaging business logic as functions deployable to AWS Lambda.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/13-serverless-spring-function)

14. **gRPC Server Integration**  
   🔹 This is a backend in Spring Boot exposing highly efficient protobuf-based RPC endpoints.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/14-grpc-server-integration)

15. **gRPC Client Interaction**  
   🔹 This is a backend in Spring Boot consuming internal microservices using gRPC channels.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/15-grpc-client-interaction)

16. **RSocket Server**  
   🔹 This is a backend in Spring Boot implementing reactive, backpressure-aware communication over RSocket.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/16-rsocket-server)

17. **Debezium CDC**  
   🔹 This is a backend in Spring Boot capturing database changes and streaming them to Kafka via Debezium.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/17-debezium-cdc)

18. **Camunda BPM**  
   🔹 This is a backend in Spring Boot orchestrating business processes through the Camunda workflow engine.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/18-camunda-bpm)

19. **Keycloak Identity**  
   🔹 This is a backend in Spring Boot delegating user authentication entirely to a Keycloak SSO server.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/19-keycloak-identity)

20. **Multi-Tenancy DB Pattern**  
   🔹 This is a backend in Spring Boot routing database connections to different schemas based on tenant IDs.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/20-multitenancy-db-pattern)

21. **Distributed Lock Redis**  
   🔹 This is a backend in Spring Boot preventing concurrent task execution using ShedLock or Redis locks.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/21-distributed-lock-redis)

22. **Apache Camel Pipes**  
   🔹 This is a backend in Spring Boot routing and transforming messages across complex system pipelines.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/22-apache-camel-pipes)

23. **GraphQL Federation**  
   🔹 This is a backend in Spring Boot composing multiple GraphQL services into a single unified graph.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/23-graphql-federation)

24. **Elastic Stack Logging**  
   🔹 This is a backend in Spring Boot shipping JSON-formatted logs to Elasticsearch via Logstash/Filebeat.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/24-elastic-stack-logging)

25. **Performance Profiling**  
   🔹 This is a backend in Spring Boot setting up continuous profiling and JVM metrics monitoring.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/25-performance-profiling)

26. **GraalVM Native Image**  
   🔹 This is a backend in Spring Boot compiling a Spring Boot app Ahead-Of-Time for lightning-fast startup.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/26-graalvm-native-image)

27. **Prometheus Metrics**  
   🔹 This is a backend in Spring Boot scraping custom business metrics efficiently via Prometheus endpoints.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/27-prometheus-metrics)

28. **Grafana Dashboards**  
   🔹 This is a backend in Spring Boot visualizing Spring Boot actuator and custom metrics with Grafana.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/28-grafana-dashboards)

29. **Chaos Monkey**  
   🔹 This is a backend in Spring Boot randomly terminating instances and injecting latency to test resilience.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/29-chaos-monkey)

30. **Blockchain Web3j**  
   🔹 This is a backend in Spring Boot interacting with Ethereum smart contracts securely from a backend.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/30-blockchain-web3j)

31. **Hexagonal Architecture**  
   🔹 This is a backend in Spring Boot structuring code into ports and adapters to isolate domain logic.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/31-hexagonal-architecture)

32. **Clean Architecture**  
   🔹 This is a backend in Spring Boot enforcing strict dependency inversion using Spring configuration.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/32-clean-architecture)

33. **Multiple Data Sources**  
   🔹 This is a backend in Spring Boot configuring multiple EntityManagerFactory beans for different databases.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/33-multiple-data-sources)

34. **Dynamic Read/Write DBs**  
   🔹 This is a backend in Spring Boot routing read requests to replicas and writes to the primary DB.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/34-dynamic-readwrite-dbs)

35. **Hazelcast Caching Server**  
   🔹 This is a backend in Spring Boot setting up a distributed application cache grid with Hazelcast.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/35-hazelcast-caching-server)

36. **Zero-Downtime Deployment**  
   🔹 This is a backend in Spring Boot handling graceful shutdown and health checks for Kubernetes rollouts.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/36-zerodowntime-deployment)

37. **K8s ConfigMap Integration**  
   🔹 This is a backend in Spring Boot reloading properties dynamically from Kubernetes ConfigMaps.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/37-k8s-configmap-integration)

38. **mTLS Authentication**  
   🔹 This is a backend in Spring Boot requiring bidirectional certificate validation for internal microservices.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/38-mtls-authentication)

39. **SAML 2.0 Integration**  
   🔹 This is a backend in Spring Boot authenticating enterprise users using the SAML protocol.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/39-saml-20-integration)

40. **Two-Factor Auth TOTP**  
   🔹 This is a backend in Spring Boot implementing Google Authenticator compatible time-based OTPs.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/40-twofactor-auth-totp)

41. **Dynamic Feature Toggles**  
   🔹 This is a backend in Spring Boot enabling or disabling features at runtime using Unleash or Togglz.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/41-dynamic-feature-toggles)

42. **GraphQL Subscriptions**  
   🔹 This is a backend in Spring Boot pushing live data updates over WebSockets using GraphQL.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/42-graphql-subscriptions)

43. **Spring WebSockets Redis**  
   🔹 This is a backend in Spring Boot scaling STOMP WebSockets horizontally using a Redis broker relay.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/43-spring-websockets-redis)

44. **Hazelcast Session Grid**  
   🔹 This is a backend in Spring Boot sharing user sessions across an entire cluster with Hazelcast.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/44-hazelcast-session-grid)

45. **OpenTelemetry Tracing**  
   🔹 This is a backend in Spring Boot instrumenting external calls adhering to OpenTelemetry standards.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/45-opentelemetry-tracing)

46. **Spring Batch Partitioning**  
   🔹 This is a backend in Spring Boot scaling batch jobs to run chunks concurrently across threads.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/46-spring-batch-partitioning)

47. **ActiveMQ Artemis Core**  
   🔹 This is a backend in Spring Boot utilizing advanced message grouping and routing in Apache Artemis.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/47-activemq-artemis-core)

48. **JPA Entity Graph**  
   🔹 This is a backend in Spring Boot optimizing complex reads and solving N+1 queries using EntityGraphs.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/48-jpa-entity-graph)

49. **JPA Criteria API**  
   🔹 This is a backend in Spring Boot building strongly-typed dynamic queries securely avoiding JPQL strings.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/49-jpa-criteria-api)

50. **AWS SQS SNS Integration**  
   🔹 This is a backend in Spring Boot passing messages natively using the Spring Cloud AWS messaging tools.  
   📦 **Dependency Manager**: Maven  
   🧪 **Testing**: Unit tests for domain logic using **JUnit 5**. Full Integration Testing using **Testcontainers**.  
   🔹 [Project directory](advanced/50-aws-sqs-sns-integration)
