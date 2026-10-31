package com.frameflow.studio.infrastructure.dao.task;

import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.frameflow.studio.cases.identity.IInitializeAccountsCase;
import com.frameflow.studio.cases.task.IExecuteProductionTaskCase;
import com.frameflow.studio.cases.task.port.IStageDelay;
import com.frameflow.studio.domain.task.adapter.repository.IProductionTaskRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.session.cookie.secure=false", "app.tasks.mock-controls-enabled=true"})
class TaskApiIT {
    private static boolean ownsTables;
    private static final java.nio.file.Path storageRoot = createStorageRoot();
    private static java.nio.file.Path createStorageRoot() {
        try { return java.nio.file.Files.createTempDirectory("frameflow-task-api-it-"); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired Environment environment;
    @Autowired IInitializeAccountsCase initializer;
    @Autowired IProductionTaskRepository repository;
    @MockitoBean IStageDelay delay;
    @MockitoSpyBean IExecuteProductionTaskCase execution;
    String origin;
    HttpClient client;
    CountDownLatch done;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.assets.storage-root", storageRoot::toString);
        registry.add("spring.datasource.url", TaskApiIT::url);
        registry.add("spring.datasource.username", () -> required("IT_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> required("IT_DB_PASSWORD"));
    }

    @BeforeAll
    static void initializeEmptyDatabase() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", required("IT_DB_USERNAME"));
        properties.setProperty("password", required("IT_DB_PASSWORD"));
        try (Connection connection = DriverManager.getConnection(url(), properties);
             Statement statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(connection.getMetaData().getDatabaseProductVersion()).startsWith("8.4.");
            try (ResultSet tables = statement.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                assertThat(tables.next()).as("integration database must be empty").isFalse();
            }
            ownsTables = true;
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema/V001__initial_schema.sql"));
        }
    }

    @AfterAll
    static void removeStorage() throws Exception {
        try (var files = java.nio.file.Files.walk(storageRoot)) {
            for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(path);
        }
    }

    @AfterAll
    static void removeOwnedTables() throws Exception {
        if (!ownsTables) return;
        Properties properties = new Properties();
        properties.setProperty("user", required("IT_DB_USERNAME"));
        properties.setProperty("password", required("IT_DB_PASSWORD"));
        try (Connection connection = DriverManager.getConnection(url(), properties);
             Statement statement = connection.createStatement()) {
            for (String table : List.of("task_input_asset", "production_task", "uploaded_asset", "user_account")) {
                statement.executeUpdate("DROP TABLE IF EXISTS " + table);
            }
        }
    }


    @BeforeEach
    void prepare() throws Exception {
        jdbc.update("DELETE FROM task_input_asset");
        jdbc.update("DELETE FROM production_task");
        jdbc.update("DELETE FROM uploaded_asset");
        initializer.initialize("test-only-a", "test-only-b");
        origin = "http://127.0.0.1:" + environment.getProperty("local.server.port");
        client = login("a");
        doAnswer(call -> {
            try { return call.callRealMethod(); }
            finally { done.countDown(); }
        }).when(execution).execute(any(), any());
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(delay).pause(anyInt());
    }

    @Test
    void loginUploadAcceptThenCompleteWithNoPollingAndRecoverHistoryAndRealImage() throws Exception {
        String asset = upload(client);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        done = new CountDownLatch(1);
        var firstStage = new AtomicBoolean(true);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (firstStage.getAndSet(false)) {
                entered.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timed out");
            }
            return null;
        }).when(delay).pause(anyInt());
        JsonNode accepted;
        String path;
        try {
            var response = submit(client, "portrait-card", List.of(asset), "{\"stageDelayMs\":17}");
            assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
            accepted = mapper.readTree(response.body());
            assertThat(accepted.size()).isEqualTo(3);
            path = response.headers().firstValue("Location").orElseThrow();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(done.getCount()).as("202 returns while background work is blocked").isEqualTo(1);
            var running = json(get(client, path));
            assertThat(running.get("status").asString()).isEqualTo("RUNNING");
            assertThat(running.get("currentStage").asString()).isEqualTo("CHECK_ASSETS");
            assertThat(running.get("revision").asLong()).isEqualTo(1);
            assertError(get(client, path + "/result"), 409, "RESULT_NOT_READY");
            var b = login("b");
            assertError(get(b, path), 404, "RESOURCE_NOT_FOUND");
            assertError(get(b, path + "/result"), 404, "RESOURCE_NOT_FOUND");
            assertThat(json(get(b, "/api/v1/tasks")).get("items").size()).isZero();
            assertError(submit(b, "portrait-card", List.of(asset), null), 404, "RESOURCE_NOT_FOUND");
        } finally { release.countDown(); }
        // No HTTP or repository polling between release and execution completion.
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        var terminal = json(get(client, path));
        assertThat(terminal.get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(terminal.get("currentStage").asString()).isEqualTo("FINALIZE");
        assertThat(terminal.get("revision").asLong()).isEqualTo(4);
        assertThat(terminal.get("ownerId")).isEqualTo(accepted.get("ownerId"));
        assertThat(terminal.get("createdAt")).isEqualTo(accepted.get("createdAt"));
        var id = UUID.fromString(accepted.get("taskId").asString());
        var owner = UUID.fromString(accepted.get("ownerId").asString());
        var persisted = repository.findOwned(id, owner).orElseThrow();
        for (int read = 0; read < 3; read++) {
            assertThat(json(get(client, path))).isEqualTo(terminal);
            assertThat(json(get(client, "/api/v1/tasks")).get("items").get(0)).isEqualTo(terminal);
        }
        var image = client.send(HttpRequest.newBuilder(URI.create(origin + path + "/result")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.body()).isEqualTo(new ClassPathResource("demo-media/portrait-result.jpg").getContentAsByteArray());
        assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image.body()))).isNotNull();
        assertThat(image.headers().firstValue("Content-Type")).contains("image/jpeg");
        assertThat(image.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(image.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(repository.findOwned(id, owner).orElseThrow()).isEqualTo(persisted);
        // A fresh login recovers the same persistent fact, independent of the original HTTP session.
        assertThat(json(get(login("a"), path))).isEqualTo(terminal);
        verify(delay, times(3)).pause(17);
    }

    @Test
    void eachSpecifiedFailureCompletesWithoutQueriesAndHistoryPreservesOrderingAndFailureFacts() throws Exception {
        String asset = upload(client);
        for (String stage : List.of("CHECK_ASSETS", "GENERATE", "FINALIZE")) {
            done = new CountDownLatch(1);
            var response = submit(client, "portrait-card", List.of(asset),
                    "{\"failAtStage\":\"" + stage + "\",\"stageDelayMs\":0}");
            assertThat(response.statusCode()).isEqualTo(202);
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            String path = response.headers().firstValue("Location").orElseThrow();
            var failed = json(get(client, path));
            assertThat(failed.get("status").asString()).isEqualTo("FAILED");
            assertThat(failed.get("currentStage").asString()).isEqualTo(stage);
            assertThat(failed.get("failure").get("kind").asString()).isEqualTo("STAGE");
            assertThat(failed.get("failure").get("stage").asString()).isEqualTo(stage);
            assertThat(failed.get("failure").get("code").asString()).isEqualTo("MOCK_STAGE_FAILURE");
            assertThat(failed.get("result").isNull()).isTrue();
            assertError(get(client, path + "/result"), 409, "RESULT_NOT_READY");
        }
        var history = json(get(client, "/api/v1/tasks")).get("items");
        assertThat(history.size()).isEqualTo(3);
        assertThat(history.get(0).get("currentStage").asString()).isEqualTo("FINALIZE");
        assertThat(history.get(1).get("currentStage").asString()).isEqualTo("GENERATE");
        assertThat(history.get(2).get("currentStage").asString()).isEqualTo("CHECK_ASSETS");
        for (var record : history) {
            assertThat(json(get(client, "/api/v1/tasks/" + record.get("taskId").asString()))).isEqualTo(record);
        }
        assertThat(json(get(login("b"), "/api/v1/tasks")).get("items").size()).isZero();
    }

    @Test
    void invalidInputsLeaveNoTaskAndRepeatedValidPositionsStayOrderedInRealBindings() throws Exception {
        String asset = upload(client);
        assertError(submit(client, "portrait-card", List.of(), null), 422, "TEMPLATE_REQUIREMENTS_MISMATCH");
        assertError(submit(client, "portrait-card", List.of(asset, asset), null), 422, "TEMPLATE_REQUIREMENTS_MISMATCH");
        assertError(submit(client, "missing", List.of(asset), null), 404, "RESOURCE_NOT_FOUND");
        assertError(submit(client, "portrait-card", List.of(UUID.randomUUID().toString()), null), 404, "RESOURCE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM production_task", Integer.class)).isZero();
        done = new CountDownLatch(1);
        var response = submit(client, "travel-postcard", List.of(asset, asset), "{\"stageDelayMs\":0}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        String id = mapper.readTree(response.body()).get("taskId").asString();
        assertThat(jdbc.queryForList("SELECT asset_id FROM task_input_asset WHERE task_id=? ORDER BY position", String.class, id))
                .containsExactly(asset, asset);
        assertThat(json(get(client, "/api/v1/tasks/" + id)).get("status").asString()).isEqualTo("SUCCEEDED");
    }

    @Test
    void bothTemplatesPersistAndServeTheirOwnResultArtwork() throws Exception {
        String asset = upload(client);
        var bodies = new java.util.ArrayList<byte[]>();
        for (var entry : java.util.Map.of("portrait-card", "portrait-result", "travel-postcard", "postcard-result").entrySet()) {
            done = new CountDownLatch(1);
            var assets = entry.getKey().equals("portrait-card") ? List.of(asset) : List.of(asset, asset);
            var accepted = submit(client, entry.getKey(), assets, "{\"stageDelayMs\":0}");
            assertThat(accepted.statusCode()).isEqualTo(202);
            String path = accepted.headers().firstValue("Location").orElseThrow();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            var task = json(get(client, path));
            assertThat(task.get("status").asString()).isEqualTo("SUCCEEDED");
            assertThat(task.get("templateId").asString()).isEqualTo(entry.getKey());
            var response = client.send(HttpRequest.newBuilder(URI.create(origin + path + "/result")).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(new ClassPathResource("demo-media/" + entry.getValue() + ".jpg").getContentAsByteArray());
            var persisted = repository.findOwned(UUID.fromString(task.get("taskId").asString()),
                    UUID.fromString(task.get("ownerId").asString())).orElseThrow();
            assertThat(persisted.result().resultReference()).isEqualTo("demo:" + entry.getValue());
            bodies.add(response.body());
        }
        assertThat(bodies.get(0)).isNotEqualTo(bodies.get(1));
    }

    private HttpClient login(String suffix) throws Exception {
        var target = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var response = target.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/auth/login"))
                .header("X-CSRF-TOKEN", token(target)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=demo-" + suffix + "&password=test-only-" + suffix)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return target;
    }

    private String token(HttpClient target) throws Exception { return json(get(target, "/api/v1/auth/csrf")).get("token").asString(); }
    private HttpResponse<String> get(HttpClient target, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin + path));
        if (path.endsWith("/result")) request.header("Accept", "image/*");
        return target.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode json(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return mapper.readTree(response.body());
    }
    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        var body = mapper.readTree(response.body());
        assertThat(body.get("error").get("code").asString()).isEqualTo(code);
        assertThat(body.get("requestId").asString()).isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
    }
    private HttpResponse<String> submit(HttpClient target, String template, List<String> assets, String simulation) throws Exception {
        String body = "{\"templateId\":\"" + template + "\",\"assetIds\":" + mapper.writeValueAsString(assets)
                + (simulation == null ? "" : ",\"simulation\":" + simulation) + "}";
        return target.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/tasks"))
                .header("X-CSRF-TOKEN", token(target)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String upload(HttpClient target) throws Exception {
        var body = new ByteArrayOutputStream();
        body.write(("--task-it\r\nContent-Disposition: form-data; name=\"templateId\"\r\n\r\nportrait-card\r\n"
                + "--task-it\r\nContent-Disposition: form-data; name=\"file\"; filename=\"input.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(new ClassPathResource("demo-media/portrait-cover.jpg").getContentAsByteArray());
        body.write("\r\n--task-it--\r\n".getBytes(StandardCharsets.UTF_8));
        var response = target.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/assets"))
                .header("X-CSRF-TOKEN", token(target)).header("Content-Type", "multipart/form-data; boundary=task-it")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return mapper.readTree(response.body()).get("assetId").asString();
    }

    private static String url() {
        String name = required("IT_DB_NAME");
        String host = System.getenv().getOrDefault("IT_DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("IT_DB_PORT", "3306");
        if (!name.matches("frameflow_it(?:_[a-z0-9_]+)?") || !host.matches("[A-Za-z0-9.-]+")
                || !port.matches("[0-9]{1,5}") || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) {
            throw new IllegalArgumentException("Use an isolated frameflow_it database and valid host/port");
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + name
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8"
                + "&connectTimeout=3000&socketTimeout=5000&sslMode=DISABLED&allowPublicKeyRetrieval=true";
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required for isolated integration tests");
        return value;
    }
}
