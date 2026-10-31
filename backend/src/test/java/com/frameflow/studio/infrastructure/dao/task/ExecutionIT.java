package com.frameflow.studio.infrastructure.dao.task;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.frameflow.studio.cases.asset.ICheckTaskAssetsCase;
import com.frameflow.studio.cases.asset.IMediaResourceCase;
import com.frameflow.studio.cases.asset.IUploadAssetCase;
import com.frameflow.studio.cases.task.IExecuteProductionTaskCase;
import com.frameflow.studio.cases.task.impl.ExecuteProductionTaskCaseImpl;
import com.frameflow.studio.cases.task.model.ExecutionOptions;
import com.frameflow.studio.domain.task.adapter.port.IMediaGeneratorPort;
import com.frameflow.studio.domain.task.adapter.repository.IProductionTaskRepository;
import com.frameflow.studio.domain.task.adapter.repository.TaskRecord;
import com.frameflow.studio.domain.task.adapter.repository.TaskSnapshot;
import com.frameflow.studio.domain.task.model.aggregate.ProductionTask;
import com.frameflow.studio.domain.task.model.valobj.TaskInput;
import com.frameflow.studio.domain.task.model.valobj.TaskStage;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;
import com.frameflow.studio.infrastructure.adapter.port.task.ExecutorTaskDispatcherPort;
import com.frameflow.studio.trigger.job.task.ProductionTaskWorker;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
class ExecutionIT {
    private static boolean ownsTables;
    private static final java.nio.file.Path storageRoot = createStorageRoot();
    private static java.nio.file.Path createStorageRoot() {
        try { return java.nio.file.Files.createTempDirectory("frameflow-execution-it-"); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    @Autowired IProductionTaskRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired ICheckTaskAssetsCase assets;
    @Autowired IMediaResourceCase media;
    @Autowired IUploadAssetCase upload;
    @Autowired IMediaGeneratorPort generator;
    @Autowired IExecuteProductionTaskCase execution;
    @Autowired Clock clock;
    @Autowired ExecutorTaskDispatcherPort configuredDispatcher;
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.assets.storage-root", storageRoot::toString);
        registry.add("spring.datasource.url", ExecutionIT::url);
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
    void clearOwnedTasks() {
        jdbc.update("DELETE FROM task_input_asset");
        jdbc.update("DELETE FROM production_task");
        jdbc.update("DELETE FROM uploaded_asset");
    }


    @Test
    void duplicateDispatchRacesOnRealMysqlOnlyWinnerDoesIoAndEachStageIsAlreadyCommitted() throws Exception {
        var task = create();
        var barrier = new CyclicBarrier(2);
        var shared = new ForwardingRepository() {
            @Override public Optional<TaskSnapshot> findById(UUID id) {
                var loaded = super.findById(id);
                if (loaded.isPresent() && loaded.get().getRevision() == 0) {
                    try { barrier.await(5, TimeUnit.SECONDS); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }
                return loaded;
            }
        };
        var calls = new CopyOnWriteArrayList<TaskStage>();
        var done = new CountDownLatch(2);
        var controlled = new ExecuteProductionTaskCaseImpl(shared, (owner, ids) -> {
            observeCommitted(task, TaskStage.CHECK_ASSETS, 1);
            assertThat(owner).isEqualTo(task.getOwnerId());
            assertThat(ids).isEqualTo(task.getInput().assetIds());
            assets.check(owner, ids);
            calls.add(TaskStage.CHECK_ASSETS);
        }, input -> {
            observeCommitted(task, TaskStage.GENERATE, 2);
            calls.add(TaskStage.GENERATE);
            return generator.generate(input);
        }, media, milliseconds -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (calls.size() == 2) observeCommitted(task, TaskStage.FINALIZE, 3);
        }, clock, transactionManager);
        var worker = new ProductionTaskWorker(controlled);
        try (var dispatcher = new ExecutorTaskDispatcherPort(2, 2, (id, options) -> {
            try { worker.execute(id, options); } finally { done.countDown(); }
        })) {
            assertThat(dispatcher.dispatch(task.getTaskId(), new ExecutionOptions(null, 0))).isTrue();
            assertThat(dispatcher.dispatch(task.getTaskId(), new ExecutionOptions(null, 0))).isTrue();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(calls).containsExactly(TaskStage.CHECK_ASSETS, TaskStage.GENERATE);
        var result = repository.findOwned(task.getTaskId(), task.getOwnerId()).orElseThrow();
        assertThat(result.status()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(result.revision()).isEqualTo(4);
        var image = media.prepareResult(result.result().resultReference());
        assertThat(image.mediaType()).isEqualTo("image/jpeg");
        assertThat(javax.imageio.ImageIO.read(new ByteArrayInputStream(image.bytes()))).isNotNull();
        execution.execute(task.getTaskId(), new ExecutionOptions(TaskStage.CHECK_ASSETS, 0));
        assertThat(repository.findOwned(task.getTaskId(), task.getOwnerId()).orElseThrow()).isEqualTo(result);
    }

    @Test
    void concurrentTasksKeepOptionsIndependentWhileOneIsBlocked() throws Exception {
        var first = create();
        var second = create();
        var blocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondDone = new CountDownLatch(1);
        var firstDone = new CountDownLatch(1);
        var controlled = new ExecuteProductionTaskCaseImpl(repository, assets, generator, media, milliseconds -> {
            if (milliseconds == 17) {
                blocked.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timed out");
            }
        }, clock, transactionManager);
        try (var dispatcher = new ExecutorTaskDispatcherPort(2, 2, (id, options) -> {
            try { controlled.execute(id, options); }
            finally { (id.equals(first.getTaskId()) ? firstDone : secondDone).countDown(); }
        })) {
            try {
                assertThat(dispatcher.dispatch(first.getTaskId(), new ExecutionOptions(null, 17))).isTrue();
                assertThat(blocked.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(dispatcher.dispatch(second.getTaskId(), new ExecutionOptions(TaskStage.GENERATE, 0))).isTrue();
                assertThat(secondDone.await(5, TimeUnit.SECONDS)).isTrue();
                var failed = repository.findOwned(second.getTaskId(), second.getOwnerId()).orElseThrow();
                assertThat(failed.status()).isEqualTo(TaskStatus.FAILED);
                assertThat(failed.currentStage()).isEqualTo(TaskStage.GENERATE);
                assertThat(failed.failure().code()).isEqualTo("MOCK_STAGE_FAILURE");
                observeCommitted(first, TaskStage.CHECK_ASSETS, 1);
            } finally { release.countDown(); }
            assertThat(firstDone.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(repository.findOwned(first.getTaskId(), first.getOwnerId()).orElseThrow().status())
                .isEqualTo(TaskStatus.SUCCEEDED);
    }

    @ParameterizedTest
    @EnumSource(TaskStage.class)
    void configuredExecutionPersistsEachSelectedFailureAndNoResult(TaskStage failed) {
        var task = create();
        execution.execute(task.getTaskId(), new ExecutionOptions(failed, 0));
        var record = repository.findOwned(task.getTaskId(), task.getOwnerId()).orElseThrow();
        assertThat(record.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(record.currentStage()).isEqualTo(failed);
        assertThat(record.failure().stage()).isEqualTo(failed);
        assertThat(record.failure().code()).isEqualTo("MOCK_STAGE_FAILURE");
        assertThat(record.revision()).isEqualTo(failed.ordinal() + 2);
        assertThat(record.result()).isNull();
    }

    @Test
    void configuredDispatcherCompletesWithoutLoginContextOrQueryingToAdvanceTask() throws Exception {
        var task = create();
        // Wait only for observation, never use a query to trigger execution.
        var terminal = new CountDownLatch(1);
        var observer = Executors.newSingleThreadExecutor();
        try {
            assertThat(configuredDispatcher.dispatch(task.getTaskId(), new ExecutionOptions(null, 0))).isTrue();
            observer.submit(() -> {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline) {
                    if (repository.findOwned(task.getTaskId(), task.getOwnerId()).orElseThrow().status() == TaskStatus.SUCCEEDED) {
                        terminal.countDown(); return;
                    }
                    try { Thread.sleep(10); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); return; }
                }
            });
            assertThat(terminal.await(6, TimeUnit.SECONDS)).isTrue();
        } finally { observer.shutdownNow(); }
        assertThat(repository.findOwned(task.getTaskId(), task.getOwnerId()).orElseThrow().revision()).isEqualTo(4);
    }

    @Test
    void missingOwnedInputIsRealCheckFailureAndGeneratorNeverRuns() {
        var task = ProductionTask.accept(UUID.randomUUID(), UUID.randomUUID(), clock.instant(),
                new TaskInput("portrait-card", List.of(UUID.randomUUID())));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> repository.insert(task));
        var generated = new AtomicInteger();
        var controlled = new ExecuteProductionTaskCaseImpl(repository, assets, input -> {
            generated.incrementAndGet(); return generator.generate(input);
        }, media, milliseconds -> {}, clock, transactionManager);
        controlled.execute(task.getTaskId(), new ExecutionOptions(null, 0));
        var result = repository.findOwned(task.getTaskId(), task.getOwnerId()).orElseThrow();
        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.currentStage()).isEqualTo(TaskStage.CHECK_ASSETS);
        assertThat(result.failure().code()).isEqualTo("ASSET_UNAVAILABLE");
        assertThat(generated.get()).isZero();
    }

    private void observeCommitted(ProductionTask task, TaskStage stage, long revision) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        // An independent physical connection sees committed state while external work is running.
        try (var connection = DriverManager.getConnection(url(), required("IT_DB_USERNAME"), required("IT_DB_PASSWORD"));
             var statement = connection.prepareStatement("SELECT status,current_stage,revision FROM production_task WHERE task_id=?")) {
            statement.setString(1, task.getTaskId().toString());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("RUNNING");
                assertThat(rows.getString(2)).isEqualTo(stage.name());
                assertThat(rows.getLong(3)).isEqualTo(revision);
            }
        } catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
    }

    private ProductionTask create() {
        UUID owner = UUID.randomUUID();
        var asset = upload.upload(owner, "portrait-card", new ByteArrayInputStream(media.resultFixture().bytes()));
        var task = ProductionTask.accept(UUID.randomUUID(), owner, clock.instant(),
                new TaskInput("portrait-card", List.of(asset.assetId())));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> repository.insert(task));
        return task;
    }

    class ForwardingRepository implements IProductionTaskRepository {
        @Override public void insert(ProductionTask task) { repository.insert(task); }
        @Override public Optional<TaskSnapshot> findById(UUID id) { return repository.findById(id); }
        @Override public boolean save(TaskSnapshot loaded, Instant at) { return repository.save(loaded, at); }
        @Override public Optional<TaskRecord> findOwned(UUID id, UUID owner) { return repository.findOwned(id, owner); }
        @Override public List<TaskRecord> listOwned(UUID owner) { return repository.listOwned(owner); }
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
