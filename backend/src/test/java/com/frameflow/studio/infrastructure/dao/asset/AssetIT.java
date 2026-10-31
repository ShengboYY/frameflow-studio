package com.frameflow.studio.infrastructure.dao.asset;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

/** Owns schema only after proving the explicitly configured disposable database is empty. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
class AssetIT {
    private static boolean ownsTables;
    @org.junit.jupiter.api.io.TempDir static java.nio.file.Path root;
    @Autowired IUploadedAssetDao dao;
    @Autowired com.frameflow.studio.domain.asset.adapter.repository.IUploadedAssetRepository repository;
    @Autowired com.frameflow.studio.cases.asset.IUploadAssetCase upload;
    @Autowired com.frameflow.studio.cases.asset.IResolveTaskAssetsCase resolve;
    @Autowired com.frameflow.studio.cases.asset.ICheckTaskAssetsCase check;
    @Autowired com.frameflow.studio.cases.asset.IMediaResourceCase media;
    @Autowired com.frameflow.studio.cases.template.ITemplateCatalogCase templates;
    @Autowired com.frameflow.studio.domain.asset.adapter.port.IAssetStoragePort storage;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.assets.storage-root", () -> root.toString());
        registry.add("spring.datasource.url", AssetIT::url);
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
    void clearOwnedAssets() throws Exception {
        jdbc.update("DELETE FROM uploaded_asset");
        try (var files = java.nio.file.Files.list(root)) {
            for (var file : files.toList()) java.nio.file.Files.delete(file);
        }
    }

    @Test
    void actualXmlDaoBindsOwnerAndRoundTripsEveryFieldWithUtcMicroseconds() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-31T01:02:03.123456Z");
        var asset = new com.frameflow.studio.domain.asset.model.entity.UploadedAsset(id, owner, "upload:" + id,
                "image/png", 8388608, created);
        repository.insert(asset);
        var po = dao.findOwned(id.toString(), owner.toString());
        assertThat(po.getAssetId()).isEqualTo(id.toString());
        assertThat(po.getOwnerId()).isEqualTo(owner.toString());
        assertThat(po.getContentKey()).isEqualTo("upload:" + id);
        assertThat(po.getMediaType()).isEqualTo("image/png");
        assertThat(po.getSizeBytes()).isEqualTo(8388608);
        assertThat(po.getCreatedAt()).isEqualTo(LocalDateTime.ofInstant(created, ZoneOffset.UTC));
        assertThat(repository.findOwned(id, owner)).contains(asset);
        assertThat(repository.findOwned(id, UUID.randomUUID())).isEmpty();
        assertThat(dao.findOwned(id.toString(), "' OR '1'='1")).isNull();
        assertThat(dao.findOwned("' OR '1'='1", owner.toString())).isNull();
        assertThatThrownBy(() -> repository.insert(asset)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uploaded_asset", Integer.class)).isEqualTo(1);
    }

    @Test
    void realUploadCommitsReusableMetadataAndContentAndWorkerRechecksRealIo() throws Exception {
        UUID owner = UUID.randomUUID();
        byte[] bytes = fixture();
        var first = upload.upload(owner, "portrait-card", new java.io.ByteArrayInputStream(bytes));
        var second = upload.upload(owner, "travel-postcard", new java.io.ByteArrayInputStream(bytes));
        assertThat(first.assetId()).isNotEqualTo(second.assetId());
        assertThat(repository.findOwned(first.assetId(), owner).orElseThrow().createdAt()).isEqualTo(first.createdAt());
        var ids = List.of(second.assetId(), first.assetId(), second.assetId());
        assertThat(resolve.resolve(owner, ids)).extracting(value -> value.assetId()).containsExactlyElementsOf(ids);
        check.check(owner, ids);
        assertThat(media.input(owner, first.assetId()).bytes()).isEqualTo(bytes);
        assertThat(new com.frameflow.studio.infrastructure.adapter.port.asset.LocalAssetStoragePort(root)
                .read(first.contentReference())).isEqualTo(bytes);
        assertThatThrownBy(() -> resolve.resolve(UUID.randomUUID(), ids)).hasMessage("NOT_FOUND");
        storage.discard(first.contentReference());
        assertThatThrownBy(() -> check.check(owner, ids)).hasMessage("UNAVAILABLE");
        assertThatThrownBy(() -> media.input(owner, first.assetId())).hasMessage("UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uploaded_asset", Integer.class)).isEqualTo(2);
    }

    @Test
    void actualTransactionRollsBackMapperWriteAndCompensatesFileWhenPersistenceFails() throws Exception {
        var failingRepository = new com.frameflow.studio.domain.asset.adapter.repository.IUploadedAssetRepository() {
            @Override public void insert(com.frameflow.studio.domain.asset.model.entity.UploadedAsset asset) {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                repository.insert(asset);
                throw new org.springframework.dao.DataAccessResourceFailureException("simulated failure after SQL insert");
            }
            @Override public Optional<com.frameflow.studio.domain.asset.model.entity.UploadedAsset> findOwned(UUID id, UUID owner) {
                return repository.findOwned(id, owner);
            }
        };
        var failing = new com.frameflow.studio.cases.asset.impl.UploadAssetCaseImpl(templates, storage, failingRepository,
                clock, transactionManager);
        var input = new java.io.ByteArrayInputStream(fixture()) {
            @Override public synchronized int read(byte[] bytes, int offset, int count) {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return super.read(bytes, offset, count);
            }
        };
        assertThatThrownBy(() -> failing.upload(UUID.randomUUID(), "portrait-card", input))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uploaded_asset", Integer.class)).isZero();
        try (var files = java.nio.file.Files.list(root)) { assertThat(files).isEmpty(); }
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                upload.upload(UUID.randomUUID(), "portrait-card", input)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("outside a transaction");
    }

    private byte[] fixture() throws Exception {
        return new ClassPathResource("demo-media/mock-result.jpg").getContentAsByteArray();
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
