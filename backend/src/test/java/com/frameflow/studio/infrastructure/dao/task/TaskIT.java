package com.frameflow.studio.infrastructure.dao.task;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.sql.DataSource;
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
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.frameflow.studio.domain.task.adapter.repository.IProductionTaskRepository;
import com.frameflow.studio.domain.task.adapter.repository.TaskRecord;
import com.frameflow.studio.domain.task.adapter.repository.TaskSnapshot;
import com.frameflow.studio.domain.task.model.aggregate.ProductionTask;
import com.frameflow.studio.domain.task.model.valobj.FailureInfo;
import com.frameflow.studio.domain.task.model.valobj.FailureKind;
import com.frameflow.studio.domain.task.model.valobj.TaskInput;
import com.frameflow.studio.domain.task.model.valobj.TaskResult;
import com.frameflow.studio.domain.task.model.valobj.TaskStage;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;
import com.frameflow.studio.infrastructure.adapter.repository.task.ProductionTaskRepository;
import com.frameflow.studio.infrastructure.dao.po.task.TaskInputAssetPO;

import static org.assertj.core.api.Assertions.*;

/** Owns tables only after verifying the explicitly configured disposable MySQL is empty. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
class TaskIT {
    private static boolean ownsTables;
    private static final Instant CREATED = Instant.parse("2026-10-31T01:02:03.123456Z");
    @Autowired IProductionTaskRepository repository;
    @Autowired IProductionTaskDao tasks;
    @Autowired ITaskInputAssetDao inputs;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TaskIT::url);
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
    }

    @Test
    void atomicCreationRestoresOrderedRepeatedInputsAndUtcMicroseconds() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var task = pending(UUID.randomUUID(), UUID.randomUUID(), CREATED, List.of(b, a, b));
        insert(task);
        var po = tasks.findById(task.getTaskId().toString());
        assertThat(po.getCreatedAt()).isEqualTo(LocalDateTime.ofInstant(CREATED, ZoneOffset.UTC));
        assertThat(po.getUpdatedAt()).isEqualTo(po.getCreatedAt());
        assertThat(po.getRevision()).isZero();
        assertThat(po.getCurrentStage()).isNull();
        assertThat(po.getResultRef()).isNull();
        assertThat(po.getFailureKind()).isNull();
        assertThat(po.getFailureCode()).isNull();
        assertThat(po.getFailureMessage()).isNull();
        assertThat(inputs.findByTaskId(po.getTaskId())).extracting(TaskInputAssetPO::getPosition)
                .containsExactly(0, 1, 2);
        TaskSnapshot restored = repository.findById(task.getTaskId()).orElseThrow();
        assertThat(restored.getTask()).usingRecursiveComparison().isEqualTo(task);
        assertThat(restored.getRevision()).isZero();
        assertThat(restored.getExpectedStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(restored.getExpectedStage()).isNull();
        assertThat(restored.getUpdatedAt()).isEqualTo(CREATED);
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
        assertThat(tasks.findById("' OR '1'='1")).isNull();
        assertThat(inputs.findByTaskId("' OR '1'='1")).isEmpty();
    }

    @Test
    void rootInsertFailureLeavesExistingRootAndBindingsUntouched() {
        var task = create();
        var duplicate = pending(task.getTaskId(), UUID.randomUUID(), CREATED.plusSeconds(1), List.of(UUID.randomUUID()));
        assertThatThrownBy(() -> insert(duplicate)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(repository.findById(task.getTaskId()).orElseThrow().getTask())
                .usingRecursiveComparison().isEqualTo(task);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM production_task", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_input_asset", Integer.class)).isEqualTo(1);
    }

    @Test
    void realSecondBindingConstraintFailureRollsBackRootAndAllEarlierBindings() {
        var brokenInputs = new ITaskInputAssetDao() {
            @Override public int insert(TaskInputAssetPO input) {
                if (input.getPosition() == 1) input.setPosition(-1);
                return inputs.insert(input);
            }
            @Override public List<TaskInputAssetPO> findByTaskId(String id) { return inputs.findByTaskId(id); }
        };
        var failing = new ProductionTaskRepository(tasks, brokenInputs);
        var task = pending(UUID.randomUUID(), UUID.randomUUID(), CREATED, List.of(UUID.randomUUID(), UUID.randomUUID()));
        assertThatThrownBy(() -> transaction(() -> { failing.insert(task); return null; }))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .rootCause().isInstanceOf(java.sql.SQLException.class)
                .extracting(value -> ((java.sql.SQLException) value).getErrorCode()).isEqualTo(3819);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM production_task", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_input_asset", Integer.class)).isZero();
    }

    @Test
    void writesRejectAutocommitAndReadOnlyTransactionsBeforeSql() {
        var task = pending(UUID.randomUUID(), UUID.randomUUID(), CREATED, List.of(UUID.randomUUID()));
        assertThatThrownBy(() -> repository.insert(task)).isInstanceOf(IllegalStateException.class);
        var readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.executeWithoutResult(status -> repository.insert(task)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM production_task", Integer.class)).isZero();
        insert(task);
        var snapshot = repository.findById(task.getTaskId()).orElseThrow();
        snapshot.getTask().startChecking();
        assertThatThrownBy(() -> repository.save(snapshot, CREATED.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> readOnly.executeWithoutResult(status -> repository.save(snapshot, CREATED.plusSeconds(1))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById(task.getTaskId()).orElseThrow().getRevision()).isZero();
    }

    @Test
    void successfulLifecyclePersistsEveryNullableConclusionAndQueriesNeverMutate() {
        var task = create();
        checkRecord(task, TaskStatus.PENDING, null, 0, null, null);
        change(task.getTaskId(), ProductionTask::startChecking);
        checkRecord(task, TaskStatus.RUNNING, TaskStage.CHECK_ASSETS, 1, null, null);
        change(task.getTaskId(), ProductionTask::completeChecking);
        checkRecord(task, TaskStatus.RUNNING, TaskStage.GENERATE, 2, null, null);
        change(task.getTaskId(), ProductionTask::completeGeneration);
        checkRecord(task, TaskStatus.RUNNING, TaskStage.FINALIZE, 3, null, null);
        var result = new TaskResult("demo:mock-result");
        change(task.getTaskId(), value -> value.succeed(result));
        checkRecord(task, TaskStatus.SUCCEEDED, TaskStage.FINALIZE, 4, result, null);
        var terminal = repository.findById(task.getTaskId()).orElseThrow();
        assertThat(transaction(() -> repository.save(terminal, CREATED.plusSeconds(20)))).isFalse();
        checkRecord(task, TaskStatus.SUCCEEDED, TaskStage.FINALIZE, 4, result, null);
    }

    @ParameterizedTest
    @EnumSource(TaskStage.class)
    void stageFailureRoundTripsKindStageCodeUnicodeMessageAndRejectsTerminalWrites(TaskStage stage) {
        var task = create();
        enter(task.getTaskId(), stage);
        var failure = new FailureInfo(FailureKind.STAGE, stage, "MOCK_STAGE_FAILURE", "该阶段模拟失败");
        change(task.getTaskId(), value -> value.failCurrentStage(stage, failure.code(), failure.message()));
        long revision = stage.ordinal() + 2L;
        checkRecord(task, TaskStatus.FAILED, stage, revision, null, failure);
        var terminal = repository.findById(task.getTaskId()).orElseThrow();
        assertThat(transaction(() -> repository.save(terminal, CREATED.plusSeconds(20)))).isFalse();
        checkRecord(task, TaskStatus.FAILED, stage, revision, null, failure);
    }

    @Test
    void dispatchFailureHasNoStageOrResultAndCannotBeReclaimed() {
        var task = create();
        var stale = repository.findById(task.getTaskId()).orElseThrow();
        change(task.getTaskId(), value -> value.failBeforeStart("DISPATCH_REJECTED", "队列已满"));
        var failure = new FailureInfo(FailureKind.DISPATCH, null, "DISPATCH_REJECTED", "队列已满");
        checkRecord(task, TaskStatus.FAILED, null, 1, null, failure);
        stale.getTask().startChecking();
        assertThat(transaction(() -> repository.save(stale, CREATED.plusSeconds(2)))).isFalse();
        var terminal = repository.findById(task.getTaskId()).orElseThrow();
        assertThat(transaction(() -> repository.save(terminal, CREATED.plusSeconds(2)))).isFalse();
    }

    @Test
    void ownerQueriesUseRootFactsAndDeterministicCreatedThenIdDescendingOrder() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var low = pending(UUID.fromString("00000000-0000-0000-0000-000000000001"), owner, CREATED, List.of(UUID.randomUUID()));
        var high = pending(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), owner, CREATED, List.of(UUID.randomUUID()));
        var latest = pending(UUID.randomUUID(), owner, CREATED.plusSeconds(1), List.of(UUID.randomUUID()));
        var otherTask = pending(UUID.randomUUID(), other, CREATED.plusSeconds(2), List.of(UUID.randomUUID()));
        for (var task : List.of(low, high, latest, otherTask)) insert(task);
        assertThat(repository.listOwned(owner)).extracting(TaskRecord::taskId)
                .containsExactly(latest.getTaskId(), high.getTaskId(), low.getTaskId());
        assertThat(repository.listOwned(other)).extracting(TaskRecord::taskId).containsExactly(otherTask.getTaskId());
        assertThat(repository.listOwned(UUID.randomUUID())).isEmpty();
        assertThat(repository.findOwned(low.getTaskId(), other)).isEmpty();
        assertThat(repository.findOwned(UUID.randomUUID(), owner)).isEmpty();
        assertThat(tasks.findOwned(low.getTaskId().toString(), "' OR '1'='1")).isNull();
        assertThat(tasks.listOwned("' OR '1'='1")).isEmpty();
        assertThat(repository.listOwned(owner)).allSatisfy(record ->
                assertThat(record).isEqualTo(repository.findOwned(record.taskId(), owner).orElseThrow()));
    }

    @Test
    void actualXmlRequiresEveryPredicateAndUpdatesOnlyMutableConclusionFields() {
        var task = create();
        var candidate = tasks.findById(task.getTaskId().toString());
        candidate.setStatus("RUNNING");
        candidate.setCurrentStage("CHECK_ASSETS");
        candidate.setUpdatedAt(LocalDateTime.ofInstant(CREATED.plusSeconds(1), ZoneOffset.UTC));
        candidate.setOwnerId(UUID.randomUUID().toString());
        candidate.setTemplateId("ignored-template");
        candidate.setCreatedAt(LocalDateTime.ofInstant(CREATED.minusSeconds(10), ZoneOffset.UTC));
        candidate.setRevision(99);
        assertThat(transaction(() -> tasks.updateConclusion(candidate, 1, "PENDING", null))).isZero();
        assertThat(transaction(() -> tasks.updateConclusion(candidate, 0, "RUNNING", null))).isZero();
        assertThat(transaction(() -> tasks.updateConclusion(candidate, 0, "PENDING", "CHECK_ASSETS"))).isZero();
        String id = candidate.getTaskId();
        candidate.setTaskId(UUID.randomUUID().toString());
        assertThat(transaction(() -> tasks.updateConclusion(candidate, 0, "PENDING", null))).isZero();
        candidate.setTaskId(id);
        assertThat(transaction(() -> tasks.updateConclusion(candidate, 0, "PENDING", null))).isEqualTo(1);
        checkRecord(task, TaskStatus.RUNNING, TaskStage.CHECK_ASSETS, 1, null, null);
        assertThat(transaction(() -> tasks.updateConclusion(candidate, 1, "RUNNING", null))).isZero();
        assertThat(transaction(() -> tasks.updateConclusion(candidate, 1, "RUNNING", "GENERATE"))).isZero();
        assertThat(repository.findById(task.getTaskId()).orElseThrow().getTask().getInput()).isEqualTo(task.getInput());
    }

    @Test
    void aSuccessfulConditionalWriteStillRollsBackWithItsCaseTransaction() {
        var task = create();
        assertThatThrownBy(() -> transaction(() -> {
            var loaded = repository.findById(task.getTaskId()).orElseThrow();
            loaded.getTask().startChecking();
            assertThat(repository.save(loaded, CREATED.plusSeconds(1))).isTrue();
            throw new IllegalStateException("failure before commit");
        })).isInstanceOf(IllegalStateException.class);
        checkRecord(task, TaskStatus.PENDING, null, 0, null, null);
    }

    @Test
    void twoIndependentTransactionsCompeteForFirstClaimAndOnlyOneCommits() throws Exception {
        var task = create();
        var results = race(task.getTaskId(), ProductionTask::startChecking, ProductionTask::startChecking);
        assertThat(results).containsExactlyInAnyOrder(true, false);
        checkRecord(task, TaskStatus.RUNNING, TaskStage.CHECK_ASSETS, 1, null, null);
    }

    @Test
    void successAndFailureRaceCannotMixConclusionsAndLateLoserCannotOverwrite() throws Exception {
        var task = create();
        enter(task.getTaskId(), TaskStage.FINALIZE);
        var staleSuccess = repository.findById(task.getTaskId()).orElseThrow();
        var staleFailure = repository.findById(task.getTaskId()).orElseThrow();
        var result = new TaskResult("demo:mock-result");
        var failure = new FailureInfo(FailureKind.STAGE, TaskStage.FINALIZE, "RESULT_UNAVAILABLE", "结果暂不可用");
        var outcomes = race(task.getTaskId(), value -> value.succeed(result),
                value -> value.failCurrentStage(TaskStage.FINALIZE, failure.code(), failure.message()));
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        TaskRecord winner = repository.findOwned(task.getTaskId(), task.getOwnerId()).orElseThrow();
        if (outcomes.get(0)) {
            checkRecord(task, TaskStatus.SUCCEEDED, TaskStage.FINALIZE, 4, result, null);
        } else {
            checkRecord(task, TaskStatus.FAILED, TaskStage.FINALIZE, 4, null, failure);
        }
        staleSuccess.getTask().succeed(result);
        staleFailure.getTask().failCurrentStage(TaskStage.FINALIZE, failure.code(), failure.message());
        assertThat(transaction(() -> repository.save(staleSuccess, CREATED.plusSeconds(10)))).isFalse();
        assertThat(transaction(() -> repository.save(staleFailure, CREATED.plusSeconds(10)))).isFalse();
        assertThat(repository.findOwned(task.getTaskId(), task.getOwnerId()).orElseThrow()).isEqualTo(winner);
        // Even a caller presenting the exact terminal version cannot reopen it.
        var po = tasks.findById(task.getTaskId().toString());
        po.setStatus("PENDING");
        po.setCurrentStage(null);
        po.setResultRef(null);
        po.setFailureKind(null);
        po.setFailureCode(null);
        po.setFailureMessage(null);
        assertThat(transaction(() -> tasks.updateConclusion(po, 4, winner.status().name(), "FINALIZE"))).isZero();
    }

    private List<Boolean> race(UUID id, Consumer<ProductionTask> first, Consumer<ProductionTask> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        var connectionIds = java.util.concurrent.ConcurrentHashMap.<Long>newKeySet();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(first, second).stream().map(action -> executor.submit(() -> transaction(() -> {
                Connection connection = DataSourceUtils.getConnection(dataSource);
                try {
                    assertThat(DataSourceUtils.isConnectionTransactional(connection, dataSource)).isTrue();
                } finally {
                    DataSourceUtils.releaseConnection(connection, dataSource);
                }
                connectionIds.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                var loaded = repository.findById(id).orElseThrow();
                action.accept(loaded.getTask());
                try { barrier.await(5, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new IllegalStateException("Race barrier failed", failure); }
                return repository.save(loaded, CREATED.plusSeconds(loaded.getRevision() + 1));
            }))).toList();
            var results = List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
            assertThat(connectionIds).as("two independent MySQL connections/transactions").hasSize(2);
            return results;
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void checkRecord(ProductionTask original, TaskStatus status, TaskStage stage, long revision,
                             TaskResult result, FailureInfo failure) {
        var expected = new TaskRecord(original.getTaskId(), original.getOwnerId(), original.getInput().templateId(),
                status, stage, revision, CREATED, CREATED.plusSeconds(revision), result, failure);
        assertThat(repository.findOwned(original.getTaskId(), original.getOwnerId()).orElseThrow()).isEqualTo(expected);
        assertThat(repository.listOwned(original.getOwnerId())).containsExactly(expected);
        var snapshot = repository.findById(original.getTaskId()).orElseThrow();
        assertThat(snapshot.getTask()).usingRecursiveComparison().isEqualTo(ProductionTask.restore(original.getTaskId(),
                original.getOwnerId(), CREATED, original.getInput(), status, stage, result, failure));
        assertThat(snapshot.getRevision()).isEqualTo(revision);
        assertThat(snapshot.getUpdatedAt()).isEqualTo(expected.updatedAt());
    }

    private void enter(UUID id, TaskStage stage) {
        change(id, ProductionTask::startChecking);
        if (stage.ordinal() >= TaskStage.GENERATE.ordinal()) change(id, ProductionTask::completeChecking);
        if (stage == TaskStage.FINALIZE) change(id, ProductionTask::completeGeneration);
    }

    private void change(UUID id, Consumer<ProductionTask> behavior) {
        assertThat(transaction(() -> {
            var loaded = repository.findById(id).orElseThrow();
            behavior.accept(loaded.getTask());
            return repository.save(loaded, CREATED.plusSeconds(loaded.getRevision() + 1));
        })).isTrue();
    }

    private ProductionTask create() {
        var task = pending(UUID.randomUUID(), UUID.randomUUID(), CREATED, List.of(UUID.randomUUID()));
        insert(task);
        return task;
    }

    private ProductionTask pending(UUID id, UUID owner, Instant created, List<UUID> assets) {
        return ProductionTask.accept(id, owner, created, new TaskInput("travel-postcard", assets));
    }

    private void insert(ProductionTask task) {
        transaction(() -> { repository.insert(task); return null; });
    }

    private <T> T transaction(Supplier<T> work) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
        return transaction.execute(status -> work.get());
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
