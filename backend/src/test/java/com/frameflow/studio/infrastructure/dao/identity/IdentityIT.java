package com.frameflow.studio.infrastructure.dao.identity;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import com.frameflow.studio.cases.identity.IAuthenticateAccountCase;
import com.frameflow.studio.cases.identity.IInitializeAccountsCase;
import com.frameflow.studio.cases.identity.impl.InitializeAccountsCaseImpl;
import com.frameflow.studio.domain.identity.adapter.port.IPasswordVerifier;
import com.frameflow.studio.domain.identity.adapter.repository.IAccountRepository;
import com.frameflow.studio.domain.identity.model.entity.Account;

import static org.assertj.core.api.Assertions.*;

/** Owns schema only after proving the explicitly configured disposable database is empty. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
class IdentityIT {
    private static boolean ownsTables;
    @Autowired IUserAccountDao dao;
    @Autowired IAccountRepository repository;
    @Autowired IInitializeAccountsCase initializer;
    @Autowired IAuthenticateAccountCase authenticate;
    @Autowired IPasswordVerifier passwords;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired Environment environment;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IdentityIT::url);
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
    void clearOwnedAccounts() {
        jdbc.update("DELETE FROM user_account");
    }

    @Test
    void actualXmlDaoRoundTripsAllFieldsAndUsesBoundParameters() {
        Instant created = Instant.parse("2026-10-30T01:02:03.123456Z");
        Account account = new Account(UUID.randomUUID(), "demo-a", "演示用户 A", passwords.encode("test-only-initial"), created);
        repository.insertIfAbsent(account);
        var po = dao.findByUsername("demo-a");
        assertThat(po.getAccountId()).isEqualTo(account.getAccountId().toString());
        assertThat(po.getDisplayName()).isEqualTo("演示用户 A");
        assertThat(po.getCreatedAt()).isEqualTo(LocalDateTime.ofInstant(created, ZoneOffset.UTC));
        assertThat(dao.findById(po.getAccountId()).getPasswordHash()).isEqualTo(account.getPasswordHash());
        assertThat(repository.findById(account.getAccountId()).orElseThrow().getCreatedAt()).isEqualTo(created);
        assertThat(dao.findByUsername("demo-a' OR '1'='1")).isNull();
        assertThat(dao.findById("' OR '1'='1")).isNull();
        assertThat(dao.findByUsername(null)).isNull();
    }

    @Test
    void initializesSaltedCost12HashesAndNeverResetsExistingAccountIdentityOrPassword() {
        String password = "test-only-shared";
        long started = System.nanoTime();
        initializer.initialize(password, password);
        Account a = repository.findByUsername("demo-a").orElseThrow();
        Account b = repository.findByUsername("demo-b").orElseThrow();
        assertThat(a.getPasswordHash()).startsWith("{bcrypt}$2a$12$").doesNotContain(password);
        assertThat(b.getPasswordHash()).isNotEqualTo(a.getPasswordHash());
        assertThat(encoder.matches(password, a.getPasswordHash())).isTrue();
        assertThat(encoder.matches(password, b.getPasswordHash())).isTrue();
        initializer.initialize(null, null);
        initializer.initialize("changed-do-not-reset", "changed-do-not-reset");
        Account restored = repository.findByUsername("demo-a").orElseThrow();
        assertThat(restored.getAccountId()).isEqualTo(a.getAccountId());
        assertThat(restored.getPasswordHash()).isEqualTo(a.getPasswordHash());
        assertThat(authenticate.authenticate("demo-a", password).ownerId()).isEqualTo(a.getAccountId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account", Integer.class)).isEqualTo(2);
        System.out.printf("IP-04 BCrypt cost 12: initialize two accounts + three comparisons in %.3f s%n",
                (System.nanoTime() - started) / 1_000_000_000.0);
    }

    @Test
    void invalidInitialSecretsFailAtomicallyAndMissingPresetCanBeInitializedLater() {
        assertThatThrownBy(() -> initializer.initialize("test-only-a", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> initializer.initialize("中".repeat(25), "test-only-b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account", Integer.class)).isZero();
        repository.insertIfAbsent(new Account(UUID.randomUUID(), "demo-a", "演示用户 A", passwords.encode("test-only-a"), clock.instant()));
        UUID id = repository.findByUsername("demo-a").orElseThrow().getAccountId();
        initializer.initialize(null, "中".repeat(24));
        assertThat(repository.findByUsername("demo-a").orElseThrow().getAccountId()).isEqualTo(id);
        assertThat(authenticate.authenticate("demo-b", "中".repeat(24)).username()).isEqualTo("demo-b");
    }

    @Test
    void duplicateUsernameNoOpCannotRekeyOrOverwritePassword() {
        Account first = new Account(UUID.randomUUID(), "demo-a", "original", "original-hash", clock.instant());
        repository.insertIfAbsent(first);
        repository.insertIfAbsent(new Account(UUID.randomUUID(), "demo-a", "replacement", "replacement-hash", clock.instant()));
        Account restored = repository.findByUsername("demo-a").orElseThrow();
        assertThat(restored.getAccountId()).isEqualTo(first.getAccountId());
        assertThat(restored.getPasswordHash()).isEqualTo("original-hash");
        assertThat(restored.getDisplayName()).isEqualTo("original");
    }

    @Test
    void initializationRollsBackBothRealMapperWritesWhenSecondWriteFails() {
        IAccountRepository failSecond = new IAccountRepository() {
            @Override public Optional<Account> findByUsername(String username) { return repository.findByUsername(username); }
            @Override public Optional<Account> findById(UUID id) { return repository.findById(id); }
            @Override public void insertIfAbsent(Account account) {
                repository.insertIfAbsent(account);
                if (account.getUsername().equals("demo-b")) throw new IllegalStateException("simulated failure after insert");
            }
        };
        var failing = new InitializeAccountsCaseImpl(failSecond, passwords, clock, transactionManager);
        assertThatThrownBy(() -> failing.initialize("test-only-a", "test-only-b"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account", Integer.class)).isZero();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> initializer.initialize("test-only-a", "test-only-b")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("outside a transaction");
    }

    @Test
    void realHttpLogsOutAccountAAndLogsInBWithDistinctStableOwners() throws Exception {
        initializer.initialize("test-only-a", "test-only-b");
        String origin = "http://127.0.0.1:" + environment.getProperty("local.server.port") + "/api/v1/auth/";
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        UUID a = repository.findByUsername("demo-a").orElseThrow().getAccountId();
        UUID b = repository.findByUsername("demo-b").orElseThrow().getAccountId();
        String[] owners = {a.toString(), b.toString()};
        for (int i = 0; i < 2; i++) {
            String suffix = i == 0 ? "a" : "b";
            String csrf = csrf(client, origin);
            var login = client.send(HttpRequest.newBuilder(URI.create(origin + "login"))
                            .header("Content-Type", "application/x-www-form-urlencoded").header("X-CSRF-TOKEN", csrf)
                            .POST(HttpRequest.BodyPublishers.ofString("username=demo-" + suffix + "&password=test-only-" + suffix)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(login.body()).get("ownerId").asString()).isEqualTo(owners[i]);
            var session = client.send(HttpRequest.newBuilder(URI.create(origin + "session")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(session.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(session.body()).get("ownerId").asString()).isEqualTo(owners[i]);
            var logout = client.send(HttpRequest.newBuilder(URI.create(origin + "logout"))
                            .header("X-CSRF-TOKEN", csrf(client, origin)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(logout.statusCode()).isEqualTo(204);
        }
        assertThat(a).isNotEqualTo(b);
    }

    private String csrf(HttpClient client, String origin) throws Exception {
        var result = client.send(HttpRequest.newBuilder(URI.create(origin + "csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(200);
        return mapper.readTree(result.body()).get("token").asString();
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
