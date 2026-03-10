package com.example.graphqlmutations;

import com.example.graphqlmutations.repository.ProjectRepository;
import com.example.graphqlmutations.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.test.tester.WebGraphQlTester;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.assertj.core.api.Assertions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Full integration tests for the GraphQL Mutations application.
 *
 * <p>This test class exercises the complete request-processing pipeline:
 * <pre>
 *   GraphQL document → Spring for GraphQL → Controller → Service → Repository → PostgreSQL
 * </pre>
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>{@link SpringBootTest} starts the full Spring application context, loading
 *       all beans, the GraphQL schema wiring, JPA configuration, and the datasource.
 *       This gives high confidence that the application boots and resolves correctly.</li>
 *   <li>{@link Testcontainers} and {@link Container} spin up a real PostgreSQL Docker
 *       container for the duration of the test class. The container is shared across all
 *       test methods to avoid the overhead of restarting PostgreSQL per test.</li>
 *   <li>{@link DynamicPropertySource} injects the container's JDBC URL, username, and
 *       password into the Spring {@code Environment} before the application context starts,
 *       overriding the defaults from {@code application.yml}.</li>
 *   <li>{@link WebGraphQlTester} wraps the {@link WebGraphQlHandler} and provides a
 *       fluent DSL for executing GraphQL documents in tests. It parses the
 *       {@code data} and {@code errors} fields and lets us navigate result paths.</li>
 *   <li>The {@code "test"} profile activates {@code application-test.yml} which sets
 *       {@code ddl-auto: create-drop} — the schema is created fresh from the JPA entities
 *       at test start and dropped at the end.</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("GraphQL Mutations integration tests (PostgreSQL + Testcontainers)")
class GraphqlMutationsIntegrationTest {

    // ── Testcontainers PostgreSQL container ───────────────────────────────────────

    /**
     * A PostgreSQL container shared by all test methods in this class.
     *
     * <p>{@code static} is crucial: JUnit 5 + Testcontainers reuses the same
     * container instance for the entire test class lifecycle, avoiding the overhead
     * of starting/stopping PostgreSQL for each individual test method.
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("testdb")
                    .withUsername("testuser")
                    .withPassword("testpass");

    /**
     * Registers the container's dynamic JDBC connection details into the Spring
     * {@link org.springframework.core.env.Environment} before the application context
     * is created. Testcontainers assigns a random host port, so we must use the
     * container's accessor methods to get the actual values at runtime.
     *
     * @param registry the property registry read by Spring Boot before context startup
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // ── Injected beans ────────────────────────────────────────────────────────────

    /**
     * {@link WebGraphQlHandler} is the core Spring for GraphQL component that processes
     * GraphQL requests on the server side. Wrapping it in a {@link WebGraphQlTester}
     * gives us in-process GraphQL execution without requiring an HTTP server.
     */
    @Autowired
    WebGraphQlHandler webGraphQlHandler;

    /**
     * Fluent DSL for executing GraphQL documents in tests. Built in {@code setUp()}
     * so each test starts with a fresh tester instance.
     */
    WebGraphQlTester graphQlTester;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    ProjectRepository projectRepository;

    /**
     * Clean the database and rebuild the tester before each test to ensure
     * tests are fully independent. Order matters: tasks must be deleted before
     * projects due to the foreign-key constraint.
     */
    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        projectRepository.deleteAll();
        graphQlTester = WebGraphQlTester.builder(webGraphQlHandler).build();
    }

    // ── Project CRUD mutation tests ───────────────────────────────────────────────

    @Test
    @DisplayName("projects query returns empty list when no projects exist")
    void projects_returnsEmptyList_whenNoData() {
        // language=GraphQL
        String query = """
                query {
                  projects {
                    id
                    name
                  }
                }
                """;

        graphQlTester.document(query)
                .execute()
                .errors().verify()  // assert no GraphQL errors in the response
                .path("projects")
                .entityList(Object.class)
                .hasSize(0);
    }

    @Test
    @DisplayName("createProject mutation persists and returns the new project")
    void createProject_persistsAndReturnsProject() {
        // language=GraphQL
        String mutation = """
                mutation {
                  createProject(input: { name: "Backend Refactor", description: "Refactor legacy services" }) {
                    id
                    name
                    description
                  }
                }
                """;

        graphQlTester.document(mutation)
                .execute()
                .errors().verify()
                .path("createProject.name").entity(String.class).isEqualTo("Backend Refactor")
                .path("createProject.description").entity(String.class).isEqualTo("Refactor legacy services")
                .path("createProject.id").hasValue();
    }

    @Test
    @DisplayName("updateProject mutation changes the project's fields")
    void updateProject_changesFields() {
        // Create first
        String id = graphQlTester.document("""
                mutation { createProject(input: { name: "Old Name", description: "Old desc" }) { id } }
                """)
                .execute().errors().verify()
                .path("createProject.id").entity(String.class).get();

        // Update
        String updateMutation = String.format("""
                mutation {
                  updateProject(id: %s, input: { name: "New Name", description: "New desc" }) {
                    name
                    description
                  }
                }
                """, id);

        graphQlTester.document(updateMutation)
                .execute()
                .errors().verify()
                .path("updateProject.name").entity(String.class).isEqualTo("New Name")
                .path("updateProject.description").entity(String.class).isEqualTo("New desc");
    }

    @Test
    @DisplayName("deleteProject mutation removes the project and returns true")
    void deleteProject_removesProject() {
        String id = graphQlTester.document("""
                mutation { createProject(input: { name: "To Delete" }) { id } }
                """)
                .execute().errors().verify()
                .path("createProject.id").entity(String.class).get();

        String deleteMutation = String.format("""
                mutation { deleteProject(id: %s) }
                """, id);

        graphQlTester.document(deleteMutation)
                .execute()
                .errors().verify()
                .path("deleteProject").entity(Boolean.class).isEqualTo(true);

        // Verify the project no longer exists
        graphQlTester.document(String.format("""
                query { project(id: %s) { id } }
                """, id))
                .execute()
                .errors().verify()
                .path("project").valueIsNull();
    }

    @Test
    @DisplayName("deleteProject cascades to delete all tasks in the project")
    void deleteProject_cascadesToTasks() {
        // Create project and a task
        String projectId = graphQlTester.document("""
                mutation { createProject(input: { name: "Cascade Test" }) { id } }
                """)
                .execute().errors().verify()
                .path("createProject.id").entity(String.class).get();

        String taskId = graphQlTester.document(String.format("""
                mutation { createTask(input: { title: "Task in project", projectId: %s }) { id } }
                """, projectId))
                .execute().errors().verify()
                .path("createTask.id").entity(String.class).get();

        // Delete the project
        graphQlTester.document(String.format("""
                mutation { deleteProject(id: %s) }
                """, projectId))
                .execute().errors().verify();

        // Verify the task was also deleted (cascade)
        graphQlTester.document(String.format("""
                query { task(id: %s) { id } }
                """, taskId))
                .execute()
                .errors().verify()
                .path("task").valueIsNull();
    }

    // ── Task CRUD mutation tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("createTask mutation persists task with TODO status and project reference")
    void createTask_persistsTaskWithTodoStatus() {
        String projectId = graphQlTester.document("""
                mutation { createProject(input: { name: "My Project" }) { id } }
                """)
                .execute().errors().verify()
                .path("createProject.id").entity(String.class).get();

        String taskMutation = String.format("""
                mutation {
                  createTask(input: {
                    title: "Fix login bug",
                    description: "Users can't log in on mobile",
                    priority: 1,
                    projectId: %s
                  }) {
                    id
                    title
                    description
                    status
                    priority
                    project {
                      name
                    }
                  }
                }
                """, projectId);

        graphQlTester.document(taskMutation)
                .execute()
                .errors().verify()
                .path("createTask.title").entity(String.class).isEqualTo("Fix login bug")
                .path("createTask.status").entity(String.class).isEqualTo("TODO")
                .path("createTask.priority").entity(Integer.class).isEqualTo(1)
                .path("createTask.project.name").entity(String.class).isEqualTo("My Project");
    }

    @Test
    @DisplayName("updateTask mutation changes title, description, and priority")
    void updateTask_changesFields() {
        String projectId = graphQlTester.document("""
                mutation { createProject(input: { name: "Project" }) { id } }
                """)
                .execute().errors().verify()
                .path("createProject.id").entity(String.class).get();

        String taskId = graphQlTester.document(String.format("""
                mutation { createTask(input: { title: "Old Title", priority: 3, projectId: %s }) { id } }
                """, projectId))
                .execute().errors().verify()
                .path("createTask.id").entity(String.class).get();

        String updateMutation = String.format("""
                mutation {
                  updateTask(id: %s, input: { title: "New Title", description: "New desc", priority: 1, projectId: %s }) {
                    title
                    description
                    priority
                  }
                }
                """, taskId, projectId);

        graphQlTester.document(updateMutation)
                .execute()
                .errors().verify()
                .path("updateTask.title").entity(String.class).isEqualTo("New Title")
                .path("updateTask.description").entity(String.class).isEqualTo("New desc")
                .path("updateTask.priority").entity(Integer.class).isEqualTo(1);
    }

    @Test
    @DisplayName("deleteTask mutation removes the task and returns true")
    void deleteTask_removesTask() {
        String projectId = graphQlTester.document("""
                mutation { createProject(input: { name: "Project" }) { id } }
                """)
                .execute().errors().verify()
                .path("createProject.id").entity(String.class).get();

        String taskId = graphQlTester.document(String.format("""
                mutation { createTask(input: { title: "Task to delete", projectId: %s }) { id } }
                """, projectId))
                .execute().errors().verify()
                .path("createTask.id").entity(String.class).get();

        graphQlTester.document(String.format("""
                mutation { deleteTask(id: %s) }
                """, taskId))
                .execute()
                .errors().verify()
                .path("deleteTask").entity(Boolean.class).isEqualTo(true);
    }

    // ── State-transition mutation tests ───────────────────────────────────────────
    // These tests are the highlight of the integration test suite.
    // They verify that the state-transition mutations correctly enforce
    // business rules across the full stack (GraphQL → Service → Database).

    @Test
    @DisplayName("startTask mutation transitions task from TODO to IN_PROGRESS")
    void startTask_transitionsToInProgress() {
        String taskId = createTaskInProject("Task to start");

        graphQlTester.document(String.format("""
                mutation {
                  startTask(id: %s) {
                    id
                    status
                  }
                }
                """, taskId))
                .execute()
                .errors().verify()
                .path("startTask.status").entity(String.class).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("completeTask mutation transitions task from TODO directly to DONE")
    void completeTask_transitionsTodoToDone() {
        String taskId = createTaskInProject("Task to complete directly");

        graphQlTester.document(String.format("""
                mutation {
                  completeTask(id: %s) {
                    id
                    status
                  }
                }
                """, taskId))
                .execute()
                .errors().verify()
                .path("completeTask.status").entity(String.class).isEqualTo("DONE");
    }

    @Test
    @DisplayName("completeTask mutation transitions task from IN_PROGRESS to DONE")
    void completeTask_transitionsInProgressToDone() {
        String taskId = createTaskInProject("Task in progress to complete");

        // Start the task first
        graphQlTester.document(String.format("""
                mutation { startTask(id: %s) { status } }
                """, taskId))
                .execute().errors().verify()
                .path("startTask.status").entity(String.class).isEqualTo("IN_PROGRESS");

        // Then complete it
        graphQlTester.document(String.format("""
                mutation {
                  completeTask(id: %s) {
                    status
                  }
                }
                """, taskId))
                .execute()
                .errors().verify()
                .path("completeTask.status").entity(String.class).isEqualTo("DONE");
    }

    @Test
    @DisplayName("reopenTask mutation transitions DONE task back to TODO")
    void reopenTask_transitionsDoneToTodo() {
        String taskId = createTaskInProject("Task to reopen");

        // Complete the task first
        graphQlTester.document(String.format("""
                mutation { completeTask(id: %s) { status } }
                """, taskId))
                .execute().errors().verify()
                .path("completeTask.status").entity(String.class).isEqualTo("DONE");

        // Reopen it
        graphQlTester.document(String.format("""
                mutation {
                  reopenTask(id: %s) {
                    status
                  }
                }
                """, taskId))
                .execute()
                .errors().verify()
                .path("reopenTask.status").entity(String.class).isEqualTo("TODO");
    }

    @Test
    @DisplayName("Full lifecycle: TODO → IN_PROGRESS → DONE → TODO (reopen)")
    void fullLifecycle_todoToInProgressToDoneToTodo() {
        String taskId = createTaskInProject("Full lifecycle task");

        // Verify initial status
        graphQlTester.document(String.format("""
                query { task(id: %s) { status } }
                """, taskId))
                .execute().errors().verify()
                .path("task.status").entity(String.class).isEqualTo("TODO");

        // Start → IN_PROGRESS
        graphQlTester.document(String.format("""
                mutation { startTask(id: %s) { status } }
                """, taskId))
                .execute().errors().verify()
                .path("startTask.status").entity(String.class).isEqualTo("IN_PROGRESS");

        // Complete → DONE
        graphQlTester.document(String.format("""
                mutation { completeTask(id: %s) { status } }
                """, taskId))
                .execute().errors().verify()
                .path("completeTask.status").entity(String.class).isEqualTo("DONE");

        // Reopen → TODO
        graphQlTester.document(String.format("""
                mutation { reopenTask(id: %s) { status } }
                """, taskId))
                .execute().errors().verify()
                .path("reopenTask.status").entity(String.class).isEqualTo("TODO");
    }

    @Test
    @DisplayName("startTask mutation returns error when task is already IN_PROGRESS")
    void startTask_returnsError_whenAlreadyInProgress() {
        String taskId = createTaskInProject("Already started task");

        // Start the task (transitions it to IN_PROGRESS)
        graphQlTester.document(String.format("""
                mutation { startTask(id: %s) { status } }
                """, taskId))
                .execute().errors().verify();

        // Try to start it again — Spring for GraphQL converts the IllegalStateException
        // into a GraphQL error. The errors list is non-empty and the data path is null.
        graphQlTester.document(String.format("""
                mutation { startTask(id: %s) { status } }
                """, taskId))
                .execute()
                .errors()
                .satisfy(errors -> Assertions.assertThat(errors).isNotEmpty())
                .path("startTask").valueIsNull();
    }

    @Test
    @DisplayName("tasksByStatus query returns only tasks with the requested status")
    void tasksByStatus_returnsFilteredTasks() {
        String projectId = graphQlTester.document("""
                mutation { createProject(input: { name: "Project" }) { id } }
                """)
                .execute().errors().verify()
                .path("createProject.id").entity(String.class).get();

        // Create two tasks
        String task1Id = graphQlTester.document(String.format("""
                mutation { createTask(input: { title: "Task 1", projectId: %s }) { id } }
                """, projectId))
                .execute().errors().verify()
                .path("createTask.id").entity(String.class).get();

        graphQlTester.document(String.format("""
                mutation { createTask(input: { title: "Task 2", projectId: %s }) { id } }
                """, projectId))
                .execute().errors().verify();

        // Start only task 1
        graphQlTester.document(String.format("""
                mutation { startTask(id: %s) { id } }
                """, task1Id))
                .execute().errors().verify();

        // Query IN_PROGRESS tasks – should find only task 1
        graphQlTester.document("""
                query { tasksByStatus(status: IN_PROGRESS) { title status } }
                """)
                .execute()
                .errors().verify()
                .path("tasksByStatus")
                .entityList(Object.class)
                .hasSize(1);
    }

    @Test
    @DisplayName("tasksByProjectAndStatus query returns filtered tasks by project and status")
    void tasksByProjectAndStatus_returnsFilteredTasks() {
        String projectId = graphQlTester.document("""
                mutation { createProject(input: { name: "Filtered Project" }) { id } }
                """)
                .execute().errors().verify()
                .path("createProject.id").entity(String.class).get();

        // Create 3 tasks: 2 TODO, 1 IN_PROGRESS
        graphQlTester.document(String.format("""
                mutation { createTask(input: { title: "Todo 1", projectId: %s }) { id } }
                """, projectId)).execute().errors().verify();

        graphQlTester.document(String.format("""
                mutation { createTask(input: { title: "Todo 2", projectId: %s }) { id } }
                """, projectId)).execute().errors().verify();

        String inProgressId = graphQlTester.document(String.format("""
                mutation { createTask(input: { title: "In Progress 1", projectId: %s }) { id } }
                """, projectId))
                .execute().errors().verify()
                .path("createTask.id").entity(String.class).get();

        graphQlTester.document(String.format("""
                mutation { startTask(id: %s) { status } }
                """, inProgressId))
                .execute().errors().verify();

        // Query TODO tasks in this project – should return 2
        graphQlTester.document(String.format("""
                query { tasksByProjectAndStatus(projectId: %s, status: TODO) { title } }
                """, projectId))
                .execute()
                .errors().verify()
                .path("tasksByProjectAndStatus")
                .entityList(Object.class)
                .hasSize(2);
    }

    @Test
    @DisplayName("searchProjects query finds projects by name fragment")
    void searchProjects_findsMatchingProjects() {
        graphQlTester.document("""
                mutation { createProject(input: { name: "Backend Refactor" }) { id } }
                """).execute().errors().verify();

        graphQlTester.document("""
                mutation { createProject(input: { name: "Frontend Redesign" }) { id } }
                """).execute().errors().verify();

        // Search for "Backend" – should match only the first project
        graphQlTester.document("""
                query { searchProjects(name: "Backend") { name } }
                """)
                .execute()
                .errors().verify()
                .path("searchProjects")
                .entityList(Object.class)
                .hasSize(1);
    }

    // ── Helper methods ────────────────────────────────────────────────────────────

    /**
     * Helper method that creates a project and a task in that project,
     * returning the task ID. Used to reduce boilerplate in state-transition tests.
     *
     * @param taskTitle the title for the task to create
     * @return the generated task ID as a String
     */
    private String createTaskInProject(String taskTitle) {
        String projectId = graphQlTester.document("""
                mutation { createProject(input: { name: "Test Project" }) { id } }
                """)
                .execute().errors().verify()
                .path("createProject.id").entity(String.class).get();

        return graphQlTester.document(String.format("""
                mutation { createTask(input: { title: "%s", projectId: %s }) { id } }
                """, taskTitle, projectId))
                .execute().errors().verify()
                .path("createTask.id").entity(String.class).get();
    }
}
