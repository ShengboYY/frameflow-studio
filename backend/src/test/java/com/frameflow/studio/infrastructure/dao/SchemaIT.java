package com.frameflow.studio.infrastructure.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Schema-level JDBC tests; repository mapping and Case behavior belong to later tasks. */
class SchemaIT {

    private static final String SCHEMA_RESOURCE = "db/schema/V001__initial_schema.sql";
    private static final List<String> TABLES = List.of(
            "production_task", "task_input_asset", "uploaded_asset", "user_account");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 30, 8, 0, 0, 123456000);
    private static final String OWNER_ID = "b92661af-8e57-4ac4-9aac-56640e063407";
    private static boolean ownsTables;
    private Connection connection;

    @BeforeAll
    static void initializeEmptyTestDatabase() throws SQLException {
        try (Connection database = openConnection()) {
            assertThat(database.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(database.getMetaData().getDatabaseProductVersion()).startsWith("8.4.");
            // DDL commits implicitly. Never reset a configured database to obtain a clean baseline.
            try (Statement statement = database.createStatement();
                 ResultSet tables = statement.executeQuery(
                         "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                assertThat(tables.next()).as("integration database must be empty before initialization").isFalse();
            }
            ownsTables = true;
            ScriptUtils.executeSqlScript(database, new ClassPathResource(SCHEMA_RESOURCE));
        }
    }

    @AfterAll
    static void removeOnlyTablesCreatedByThisSuite() throws SQLException {
        if (ownsTables) {
            try (Connection database = openConnection(); Statement statement = database.createStatement()) {
                for (String table : List.of("task_input_asset", "production_task", "uploaded_asset", "user_account")) {
                    statement.executeUpdate("DROP TABLE IF EXISTS " + table);
                }
            }
        }
    }

    @BeforeEach
    void beginTestTransaction() throws SQLException {
        connection = openConnection();
        connection.setAutoCommit(false);
    }

    @AfterEach
    void rollBackTestData() throws SQLException {
        if (connection != null) {
            try (Connection database = connection) {
                database.rollback();
            }
        }
    }

    @Test
    void createsExactlyFourEmptyInnoDbTablesWithUtf8mb4() throws SQLException {
        assertThat(rows("""
                SELECT table_name, engine, table_collation FROM information_schema.tables
                WHERE table_schema = DATABASE() ORDER BY table_name
                """)).containsExactly(
                List.of("production_task", "InnoDB", "utf8mb4_0900_ai_ci"),
                List.of("task_input_asset", "InnoDB", "utf8mb4_0900_ai_ci"),
                List.of("uploaded_asset", "InnoDB", "utf8mb4_0900_ai_ci"),
                List.of("user_account", "InnoDB", "utf8mb4_0900_ai_ci"));
        for (String table : TABLES) {
            assertThat(rows("SELECT COUNT(*) FROM " + table)).containsExactly(List.of("0"));
        }
    }

    @Test
    void preservesColumnTypesNullabilityPrecisionAndMachineValueCollation() throws SQLException {
        assertThat(rows("""
                SELECT table_name, column_name, column_type, is_nullable,
                       COALESCE(collation_name, '-'), COALESCE(CAST(datetime_precision AS CHAR), '-')
                FROM information_schema.columns WHERE table_schema = DATABASE()
                ORDER BY table_name, ordinal_position
                """)).containsExactly(
                column("production_task", "task_id", "char(36)", "NO", "ascii_bin", "-"),
                column("production_task", "owner_id", "char(36)", "NO", "ascii_bin", "-"),
                column("production_task", "template_id", "varchar(64)", "NO", "ascii_bin", "-"),
                column("production_task", "status", "varchar(16)", "NO", "ascii_bin", "-"),
                column("production_task", "current_stage", "varchar(16)", "YES", "ascii_bin", "-"),
                column("production_task", "revision", "bigint", "NO", "-", "-"),
                column("production_task", "result_ref", "varchar(128)", "YES", "ascii_bin", "-"),
                column("production_task", "failure_kind", "varchar(16)", "YES", "ascii_bin", "-"),
                column("production_task", "failure_code", "varchar(64)", "YES", "ascii_bin", "-"),
                column("production_task", "failure_message", "varchar(512)", "YES", "utf8mb4_0900_ai_ci", "-"),
                column("production_task", "created_at", "datetime(6)", "NO", "-", "6"),
                column("production_task", "updated_at", "datetime(6)", "NO", "-", "6"),
                column("task_input_asset", "task_id", "char(36)", "NO", "ascii_bin", "-"),
                column("task_input_asset", "position", "smallint", "NO", "-", "-"),
                column("task_input_asset", "asset_id", "char(36)", "NO", "ascii_bin", "-"),
                column("uploaded_asset", "asset_id", "char(36)", "NO", "ascii_bin", "-"),
                column("uploaded_asset", "owner_id", "char(36)", "NO", "ascii_bin", "-"),
                column("uploaded_asset", "content_key", "varchar(128)", "NO", "ascii_bin", "-"),
                column("uploaded_asset", "media_type", "varchar(32)", "NO", "ascii_bin", "-"),
                column("uploaded_asset", "size_bytes", "bigint", "NO", "-", "-"),
                column("uploaded_asset", "created_at", "datetime(6)", "NO", "-", "6"),
                column("user_account", "account_id", "char(36)", "NO", "ascii_bin", "-"),
                column("user_account", "username", "varchar(32)", "NO", "ascii_bin", "-"),
                column("user_account", "display_name", "varchar(64)", "NO", "utf8mb4_0900_ai_ci", "-"),
                column("user_account", "password_hash", "varchar(255)", "NO", "ascii_bin", "-"),
                column("user_account", "created_at", "datetime(6)", "NO", "-", "6"));
        assertThat(rows("""
                SELECT column_name, column_default FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'production_task'
                  AND column_name IN ('revision', 'current_stage', 'result_ref',
                                      'failure_kind', 'failure_code', 'failure_message')
                ORDER BY column_name
                """)).containsExactly(
                Arrays.asList("current_stage", null), Arrays.asList("failure_code", null),
                Arrays.asList("failure_kind", null), Arrays.asList("failure_message", null),
                Arrays.asList("result_ref", null), List.of("revision", "0"));
    }

    @Test
    void createsOnlyRequiredIndexesIncludingDescendingHistoryOrder() throws SQLException {
        assertThat(rows("""
                SELECT table_name, index_name, CAST(non_unique AS CHAR), column_name, collation
                FROM information_schema.statistics WHERE table_schema = DATABASE()
                ORDER BY table_name, index_name, seq_in_index
                """)).containsExactly(
                List.of("production_task", "idx_production_task_owner_created", "1", "owner_id", "A"),
                List.of("production_task", "idx_production_task_owner_created", "1", "created_at", "D"),
                List.of("production_task", "idx_production_task_owner_created", "1", "task_id", "D"),
                List.of("production_task", "PRIMARY", "0", "task_id", "A"),
                List.of("task_input_asset", "PRIMARY", "0", "task_id", "A"),
                List.of("task_input_asset", "PRIMARY", "0", "position", "A"),
                List.of("uploaded_asset", "PRIMARY", "0", "asset_id", "A"),
                List.of("uploaded_asset", "uk_uploaded_asset_content_key", "0", "content_key", "A"),
                List.of("user_account", "PRIMARY", "0", "account_id", "A"),
                List.of("user_account", "uk_user_account_username", "0", "username", "A"));
    }

    @Test
    void hasOnlyAggregateInternalForeignKeyWithRestrictRules() throws SQLException {
        assertThat(rows("""
                SELECT k.table_name, k.column_name, k.referenced_table_name, k.referenced_column_name,
                       r.delete_rule, r.update_rule
                FROM information_schema.key_column_usage k
                JOIN information_schema.referential_constraints r
                  ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
                WHERE k.table_schema = DATABASE()
                """)).containsExactly(List.of(
                "task_input_asset", "task_id", "production_task", "task_id", "RESTRICT", "RESTRICT"));

        String taskId = id();
        String assetId = id();
        reject(1452, () -> insertInput(taskId, 0, assetId));
        insertTask(taskId, pending(), 0, CREATED_AT);
        insertInput(taskId, 0, assetId);
        reject(1451, () -> execute("DELETE FROM production_task WHERE task_id = ?", taskId));
        reject(1451, () -> execute("UPDATE production_task SET task_id = ? WHERE task_id = ?", id(), taskId));
        // No cross-domain FK: owner and asset existence/authorization must be checked by Cases.
        assertThat(rows("SELECT COUNT(*) FROM user_account")).containsExactly(List.of("0"));
        assertThat(rows("SELECT COUNT(*) FROM uploaded_asset")).containsExactly(List.of("0"));
    }

    @Test
    void enforcesPrimaryAndUniqueKeysWithoutContentHashDeduplication() throws SQLException {
        String accountId = id();
        insertAccount(accountId, "demo-a", "演示用户 A");
        reject(1062, () -> insertAccount(id(), "demo-a", "重复用户名"));
        reject(1062, () -> insertAccount(accountId, "demo-b", "重复主键"));

        String assetId = id();
        insertAsset(assetId, "content-a", "image/png", 1);
        reject(1062, () -> insertAsset(id(), "content-a", "image/png", 1));
        reject(1062, () -> insertAsset(assetId, "content-b", "image/png", 1));
        insertAsset(id(), "content-b", "image/png", 1);
        insertAsset(id(), "CONTENT-B", "image/png", 1);

        String taskId = id();
        insertTask(taskId, pending(), 0, CREATED_AT);
        reject(1062, () -> insertTask(taskId, pending(), 0, CREATED_AT));
        insertInput(taskId, 0, assetId);
        insertInput(taskId, 1, assetId);
        reject(1062, () -> insertInput(taskId, 1, id()));
        assertThat(rows("SELECT position, asset_id FROM task_input_asset ORDER BY position"))
                .containsExactly(List.of("0", assetId), List.of("1", assetId));
    }

    @Test
    void acceptsExactlyTheNineConclusionsAcrossNullEmptyAndUnknownValues() throws SQLException {
        List<String> statuses = Arrays.asList(null, "PENDING", "RUNNING", "SUCCEEDED", "FAILED", "UNKNOWN");
        List<String> stages = Arrays.asList(null, "CHECK_ASSETS", "GENERATE", "FINALIZE", "UNKNOWN");
        List<String> results = Arrays.asList(null, "", "mock-result");
        List<String> kinds = Arrays.asList(null, "STAGE", "DISPATCH", "UNKNOWN");
        List<String> codes = Arrays.asList(null, "", "MOCK_STAGE_FAILURE");
        List<String> messages = Arrays.asList(null, "", "模拟制作失败");
        int accepted = 0;
        int checked = 0;
        String taskId = id();
        for (String status : statuses) {
            for (String stage : stages) {
                for (String result : results) {
                    for (String kind : kinds) {
                        for (String code : codes) {
                            for (String message : messages) {
                                Conclusion conclusion = new Conclusion(status, stage, result, kind, code, message);
                                checked++;
                                if (isLegal(conclusion)) {
                                    insertTask(taskId, conclusion, 0, CREATED_AT);
                                    execute("DELETE FROM production_task WHERE task_id = ?", taskId);
                                    accepted++;
                                } else {
                                    SQLException failure = assertThrows(SQLException.class,
                                            () -> insertTask(taskId, conclusion, 0, CREATED_AT), conclusion.toString());
                                    assertThat(failure.getErrorCode()).as(conclusion.toString())
                                            .isEqualTo(status == null ? 1048 : 3819);
                                }
                            }
                        }
                    }
                }
            }
        }
        assertThat(checked).isEqualTo(3240);
        assertThat(accepted).isEqualTo(9);
    }

    @Test
    void rejectsInvalidUpdatesAndCaseMismatches() throws SQLException {
        String taskId = id();
        insertTask(taskId, pending(), 0, CREATED_AT);
        for (String status : List.of("pending", "PENDING ", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED")) {
            reject(3819, () -> execute("UPDATE production_task SET status = ? WHERE task_id = ?", status, taskId));
        }
        reject(3819, () -> execute("""
                UPDATE production_task SET status = 'RUNNING', current_stage = 'generate' WHERE task_id = ?
                """, taskId));
        reject(3819, () -> execute("""
                UPDATE production_task SET status = 'RUNNING', current_stage = 'GENERATE ' WHERE task_id = ?
                """, taskId));
        reject(3819, () -> insertTask(id(), new Conclusion(
                "FAILED", "GENERATE", null, "stage", "MOCK_STAGE_FAILURE", "失败"), 0, CREATED_AT));
        reject(3819, () -> insertTask(id(), new Conclusion(
                "FAILED", "GENERATE", null, "STAGE ", "MOCK_STAGE_FAILURE", "失败"), 0, CREATED_AT));
        reject(3819, () -> insertTask(id(), new Conclusion(
                "SUCCEEDED", "FINALIZE", "   ", null, null, null), 0, CREATED_AT));
        reject(3819, () -> insertTask(id(), new Conclusion(
                "FAILED", null, null, "DISPATCH", "   ", "失败"), 0, CREATED_AT));
        reject(3819, () -> insertTask(id(), new Conclusion(
                "FAILED", null, null, "DISPATCH", "DISPATCH_REJECTED", "   "), 0, CREATED_AT));
        assertThat(rows("SELECT status, current_stage, revision FROM production_task"))
                .containsExactly(Arrays.asList("PENDING", null, "0"));
    }

    @Test
    void enforcesRevisionAndTimestampBoundariesWithoutFreezingWorkflowStepCount() throws SQLException {
        reject(3819, () -> insertTask(id(), pending(), -1, CREATED_AT));
        reject(3819, () -> insertTask(id(), pending(), 0, CREATED_AT.minusNanos(1000)));
        String taskId = id();
        insertTask(taskId, pending(), 99, CREATED_AT.plusNanos(1000));
        assertThat(rows("SELECT revision FROM production_task WHERE task_id = ?", taskId))
                .containsExactly(List.of("99"));
        execute("""
                INSERT INTO production_task (task_id, owner_id, template_id, status, created_at, updated_at)
                VALUES (?, ?, 'portrait-card', 'PENDING', ?, ?)
                """, id(), OWNER_ID, CREATED_AT, CREATED_AT);
        assertThat(rows("SELECT revision FROM production_task WHERE revision = 0")).containsExactly(List.of("0"));
    }

    @Test
    void enforcesAssetByteAndMimeBoundariesAndInputPosition() throws SQLException {
        insertAsset(id(), "min", "image/jpeg", 1);
        insertAsset(id(), "max", "image/png", 8388608);
        for (long size : new long[] {-1, 0, 8388609}) {
            reject(3819, () -> insertAsset(id(), id(), "image/png", size));
        }
        reject(3819, () -> insertAsset(id(), id(), "image/gif", 1));
        reject(3819, () -> insertAsset(id(), id(), "IMAGE/PNG", 1));
        reject(3819, () -> insertAsset(id(), id(), "image/png ", 1));
        reject(3819, () -> insertAsset(id(), " ", "image/png", 1));
        String taskId = id();
        insertTask(taskId, pending(), 0, CREATED_AT);
        reject(3819, () -> insertInput(taskId, -1, id()));
        insertInput(taskId, 0, id());
        // Contiguous positions and template-specific counts require a Case, not a single-row CHECK.
        insertInput(taskId, 2, id());
    }

    @Test
    void usesUtf8mb4CharacterLengthsAndRejectsOversizedOrNonAsciiMachineValues() throws SQLException {
        assertThat(rows("SELECT @@SESSION.sql_mode").get(0).get(0)).contains("STRICT_TRANS_TABLES");
        insertAccount(id(), "a".repeat(32), "图".repeat(64));
        reject(1406, () -> insertAccount(id(), "a".repeat(33), "用户"));
        reject(1406, () -> insertAccount(id(), "demo-b", "图".repeat(65)));
        reject(3819, () -> insertAccount(id(), "DEMO-B", "用户"));
        reject(3819, () -> insertAccount(id(), " ", "用户"));
        reject(1366, () -> insertAccount(id(), "用户", "用户"));

        String message = "图😀".repeat(256);
        String taskId = id();
        insertTask(taskId, new Conclusion("FAILED", "GENERATE", null, "STAGE", "c".repeat(64), message),
                0, CREATED_AT);
        assertThat(rows("SELECT CHAR_LENGTH(failure_message), OCTET_LENGTH(failure_message) FROM production_task"))
                .containsExactly(List.of("512", "1792"));
        reject(1406, () -> insertTask(id(), new Conclusion(
                "FAILED", "GENERATE", null, "STAGE", "c".repeat(65), "失败"), 0, CREATED_AT));
        reject(1406, () -> insertTask(id(), new Conclusion(
                "FAILED", "GENERATE", null, "STAGE", "CODE", message + "图"), 0, CREATED_AT));
        insertTask(id(), new Conclusion("SUCCEEDED", "FINALIZE", "r".repeat(128), null, null, null),
                0, CREATED_AT);
        reject(1406, () -> insertTask(id(), new Conclusion(
                "SUCCEEDED", "FINALIZE", "r".repeat(129), null, null, null), 0, CREATED_AT));
    }

    @Test
    void roundTripsUtcMicrosecondsWithExplicitConversionAcrossSessionTimeZones() throws SQLException {
        assertThat(rows("SELECT @@SESSION.time_zone")).containsExactly(List.of("+00:00"));
        Instant instant = Instant.parse("2026-07-15T23:59:59.654321Z");
        execute("SET time_zone = '+08:00'");
        String taskId = id();
        LocalDateTime utc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        execute("""
                INSERT INTO production_task (task_id, owner_id, template_id, status, created_at, updated_at)
                VALUES (?, ?, 'portrait-card', 'PENDING', ?, ?)
                """, taskId, OWNER_ID, utc, utc);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT created_at FROM production_task WHERE task_id = ?")) {
            statement.setString(1, taskId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1, LocalDateTime.class).toInstant(ZoneOffset.UTC)).isEqualTo(instant);
            }
        }
        execute("SET time_zone = '+00:00'");
        assertThat(rows("SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') FROM production_task"))
                .containsExactly(List.of("2026-07-15 23:59:59.654321"));
    }

    @Test
    void rollsBackTaskRootAndBindingsTogetherWhenAnInputInsertFails() throws SQLException {
        String taskId = id();
        insertTask(taskId, pending(), 0, CREATED_AT);
        insertInput(taskId, 0, id());
        reject(1062, () -> insertInput(taskId, 0, id()));
        connection.rollback();
        assertThat(rows("SELECT COUNT(*) FROM production_task")).containsExactly(List.of("0"));
        assertThat(rows("SELECT COUNT(*) FROM task_input_asset")).containsExactly(List.of("0"));
    }

    @Test
    void initialScriptRefusesReplayInsteadOfSilentlyResettingExistingTables() throws SQLException {
        try (Connection database = openConnection()) {
            assertThatThrownBy(() -> ScriptUtils.executeSqlScript(database, new ClassPathResource(SCHEMA_RESOURCE)))
                    .hasRootCauseInstanceOf(SQLException.class)
                    .rootCause().extracting("errorCode").isEqualTo(1050);
        }
        assertThat(rows("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"))
                .containsExactly(List.of("4"));
    }

    private static Connection openConnection() throws SQLException {
        String schema = requiredEnvironment("IT_DB_NAME");
        if (!schema.matches("frameflow_it(?:_[a-z0-9_]+)?")) {
            throw new IllegalArgumentException("IT_DB_NAME must be frameflow_it or start with frameflow_it_");
        }
        String host = System.getenv().getOrDefault("IT_DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("IT_DB_PORT", "3306");
        if (!host.matches("[A-Za-z0-9.-]+") || !port.matches("[0-9]{1,5}")
                || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) {
            throw new IllegalArgumentException("Invalid integration database host or port");
        }
        Properties properties = new Properties();
        properties.setProperty("user", requiredEnvironment("IT_DB_USERNAME"));
        properties.setProperty("password", requiredEnvironment("IT_DB_PASSWORD"));
        return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + schema
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8"
                + "&connectTimeout=3000&socketTimeout=5000&sslMode=DISABLED&allowPublicKeyRetrieval=true", properties);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required; use a dedicated empty MySQL 8.4 test database");
        }
        return value;
    }

    private List<List<String>> rows(String sql, Object... arguments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet result = statement.executeQuery()) {
                List<List<String>> rows = new ArrayList<>();
                while (result.next()) {
                    List<String> row = new ArrayList<>();
                    for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
                        row.add(result.getString(column));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private void execute(String sql, Object... arguments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... arguments) throws SQLException {
        for (int index = 0; index < arguments.length; index++) {
            statement.setObject(index + 1, arguments[index]);
        }
    }

    private void insertTask(String taskId, Conclusion conclusion, long revision, LocalDateTime updatedAt)
            throws SQLException {
        execute("""
                INSERT INTO production_task
                    (task_id, owner_id, template_id, status, current_stage, revision, result_ref,
                     failure_kind, failure_code, failure_message, created_at, updated_at)
                VALUES (?, ?, 'portrait-card', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, taskId, OWNER_ID, conclusion.status(), conclusion.stage(), revision, conclusion.result(),
                conclusion.kind(), conclusion.code(), conclusion.message(), CREATED_AT, updatedAt);
    }

    private void insertAccount(String accountId, String username, String displayName) throws SQLException {
        execute("""
                INSERT INTO user_account (account_id, username, display_name, password_hash, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, accountId, username, displayName, "test-only-placeholder", CREATED_AT);
    }

    private void insertAsset(String assetId, String contentKey, String mediaType, long size) throws SQLException {
        execute("""
                INSERT INTO uploaded_asset (asset_id, owner_id, content_key, media_type, size_bytes, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, assetId, OWNER_ID, contentKey, mediaType, size, CREATED_AT);
    }

    private void insertInput(String taskId, int position, String assetId) throws SQLException {
        execute("INSERT INTO task_input_asset (task_id, position, asset_id) VALUES (?, ?, ?)",
                taskId, position, assetId);
    }

    private static void reject(int errorCode, SqlAction action) {
        SQLException failure = assertThrows(SQLException.class, action::run);
        assertThat(failure.getErrorCode()).isEqualTo(errorCode);
    }

    private static List<String> column(String... values) {
        return List.of(values);
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static Conclusion pending() {
        return new Conclusion("PENDING", null, null, null, null, null);
    }

    private static boolean isLegal(Conclusion value) {
        boolean knownStage = value.stage() != null
                && List.of("CHECK_ASSETS", "GENERATE", "FINALIZE").contains(value.stage());
        boolean noFailure = value.kind() == null && value.code() == null && value.message() == null;
        boolean hasFailure = value.code() != null && !value.code().isBlank()
                && value.message() != null && !value.message().isBlank();
        if (value.status() == null) {
            return false;
        }
        return switch (value.status()) {
            case "PENDING" -> value.stage() == null && value.result() == null && noFailure;
            case "RUNNING" -> knownStage && value.result() == null && noFailure;
            case "SUCCEEDED" -> "FINALIZE".equals(value.stage())
                    && value.result() != null && !value.result().isBlank() && noFailure;
            case "FAILED" -> value.result() == null && hasFailure
                    && (("STAGE".equals(value.kind()) && knownStage)
                    || ("DISPATCH".equals(value.kind()) && value.stage() == null));
            default -> false;
        };
    }

    private record Conclusion(String status, String stage, String result, String kind, String code, String message) { }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
}
