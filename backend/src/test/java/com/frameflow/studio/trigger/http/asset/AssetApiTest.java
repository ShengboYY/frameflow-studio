package com.frameflow.studio.trigger.http.asset;

import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.ObjectMapper;
import com.frameflow.studio.cases.identity.IAuthenticateAccountCase;
import com.frameflow.studio.cases.identity.model.AccountView;
import com.frameflow.studio.domain.asset.adapter.repository.IUploadedAssetRepository;
import com.frameflow.studio.domain.asset.model.entity.UploadedAsset;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
class AssetApiTest {
    @TempDir static Path root;
    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) { registry.add("app.assets.storage-root", () -> root.toString()); }
    @Autowired Environment environment;
    @Autowired ObjectMapper mapper;
    @MockitoBean IUploadedAssetRepository repository;
    @MockitoBean IAuthenticateAccountCase authenticate;
    @MockitoBean PlatformTransactionManager transactions;
    private final Map<UUID, UploadedAsset> records = new ConcurrentHashMap<>();
    private final UUID ownerA = UUID.randomUUID();
    private final UUID ownerB = UUID.randomUUID();
    private HttpClient client;
    private String csrf;
    private String origin;

    @BeforeEach
    void prepare() throws Exception {
        records.clear();
        try (var files = Files.list(root)) {
            for (var file : files.toList()) Files.delete(file);
        }
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        doAnswer(call -> { UploadedAsset asset = call.getArgument(0); records.put(asset.assetId(), asset); return null; })
                .when(repository).insert(any());
        when(repository.findOwned(any(), any())).thenAnswer(call -> Optional.ofNullable(records.get(call.getArgument(0)))
                .filter(asset -> asset.ownerId().equals(call.getArgument(1))));
        when(authenticate.authenticate(eq("demo-a"), anyString())).thenReturn(new AccountView(ownerA, "demo-a", "演示用户 A"));
        when(authenticate.authenticate(eq("demo-b"), anyString())).thenReturn(new AccountView(ownerB, "demo-b", "演示用户 B"));
        origin = "http://127.0.0.1:" + environment.getProperty("local.server.port") + "/api/v1";
        client = login("demo-a");
        csrf = token(client);
    }

    @Test
    void actualMultipartIgnoresFilenameAndDeclaredMimeAndPrivateReadChecksOwnership() throws Exception {
        byte[] bytes = fixture();
        var response = upload(client, body("travel-postcard", bytes, "../../secret.png", "text/plain"), csrf);
        assertThat(response.statusCode()).isEqualTo(201);
        var json = mapper.readTree(response.body());
        assertThat(json.size()).isEqualTo(6);
        assertThat(json.get("ownerId").asString()).isEqualTo(ownerA.toString());
        assertThat(json.get("mediaType").asString()).isEqualTo("image/jpeg");
        assertThat(json.get("sizeBytes").asLong()).isEqualTo(bytes.length);
        assertThat(json.get("createdAt").asString()).endsWith("Z");
        assertThat(response.body()).doesNotContain("upload:", "contentKey", root.toString(), "secret");
        String url = json.get("previewUrl").asString();
        var image = get(client, url);
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.body()).isEqualTo(bytes);
        assertThat(image.headers().firstValue("Content-Type")).contains("image/jpeg");
        assertThat(image.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(image.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        var other = login("demo-b");
        assertError(get(other, url), 404, "RESOURCE_NOT_FOUND");
        assertError(get(other, "/api/v1/assets/" + UUID.randomUUID() + "/content"), 404, "RESOURCE_NOT_FOUND");
        UUID id = UUID.fromString(json.get("assetId").asString());
        Files.delete(root.resolve(id + ".image"));
        assertError(get(client, url), 503, "RESOURCE_UNAVAILABLE");
    }

    @Test
    void enforcesGlobalFileRequestAndSmallerTemplateLimitsAtRealServletBoundary() throws Exception {
        byte[] at8 = Arrays.copyOf(fixture(), 8388608);
        assertThat(upload(client, body("travel-postcard", at8, "file.jpg", "image/jpeg"), csrf).statusCode()).isEqualTo(201);
        assertError(upload(client, body("travel-postcard", Arrays.copyOf(at8, 8388609), "file.jpg", "image/jpeg"), csrf), 413, "PAYLOAD_TOO_LARGE");
        assertThat(upload(client, body("portrait-card", Arrays.copyOf(at8, 5242880), "file.jpg", "image/jpeg"), csrf).statusCode()).isEqualTo(201);
        assertError(upload(client, body("portrait-card", Arrays.copyOf(at8, 5242881), "file.jpg", "image/jpeg"), csrf), 413, "PAYLOAD_TOO_LARGE");
        byte[] tooLargeRequest = multipart(new String[]{"templateId", "extra", "file"},
                new byte[][]{"travel-postcard".getBytes(), new byte[1048576], at8}, new String[]{null, "extra", "file.jpg"},
                new String[]{null, null, "image/jpeg"});
        assertError(upload(client, tooLargeRequest, csrf), 413, "PAYLOAD_TOO_LARGE");
        assertThat(records).hasSize(2);
    }

    @Test
    void malformedPartsUnknownTemplateInvalidContentAndDatabaseFailureUseStandardErrors() throws Exception {
        byte[] bytes = fixture();
        assertError(upload(client, body("unknown", bytes, "a.jpg", "image/jpeg"), csrf), 404, "RESOURCE_NOT_FOUND");
        assertError(upload(client, body("portrait-card", new byte[0], "a.jpg", "image/jpeg"), csrf), 422, "INVALID_IMAGE");
        assertError(upload(client, body("portrait-card", "not image".getBytes(), "a.jpg", "image/jpeg"), csrf), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertError(upload(client, body("portrait-card", Arrays.copyOf(bytes, bytes.length / 2), "a.jpg", "image/jpeg"), csrf), 422, "INVALID_IMAGE");
        for (String[] names : new String[][]{{"file"}, {"file", "file"}, {"templateId", "file", "ownerId"}, {"templateId", "templateId"}}) {
            byte[][] values = new byte[names.length][];
            String[] files = new String[names.length];
            String[] types = new String[names.length];
            for (int i = 0; i < names.length; i++) {
                values[i] = names[i].equals("file") ? bytes : "portrait-card".getBytes();
                files[i] = names[i].equals("file") ? "x.png" : null;
            }
            assertError(upload(client, multipart(names, values, files, types), csrf), 400, "INVALID_REQUEST");
        }
        doThrow(new DataAccessResourceFailureException("private SQL details")).when(repository).insert(any());
        assertError(upload(client, body("portrait-card", bytes, "a.jpg", "image/jpeg"), csrf), 503, "SERVICE_UNAVAILABLE");
        assertThat(records).isEmpty();
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
        assertError(get(client, "/api/v1/assets/not-a-uuid/content"), 400, "INVALID_REQUEST");
        assertError(get(client, "/api/v1/assets/" + UUID.randomUUID() + "/content?ownerId=x"), 400, "INVALID_REQUEST");
    }

    @Test
    void compressedImagesOverDecodeLimitsReturn413WithoutMetadataOrFiles() throws Exception {
        for (byte[] bytes : new byte[][]{
                com.frameflow.studio.support.PngFixtures.header(50_000_000, 1),
                com.frameflow.studio.support.PngFixtures.solid(4000, 4001, 8, false),
                com.frameflow.studio.support.PngFixtures.solid(3000, 3000, 16, true)}) {
            assertThat(bytes.length).isLessThan(5242880);
            assertError(upload(client, body("portrait-card", bytes, "small.png", "image/png"), csrf),
                    413, "PAYLOAD_TOO_LARGE");
        }
        verify(repository, never()).insert(any());
        verifyNoInteractions(transactions);
        assertThat(records).isEmpty();
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    @Test
    void csrfAndAuthenticationAreRequiredBeforeUploadAndMalformedMultipartIs400() throws Exception {
        var anonymous = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        assertError(upload(anonymous, body("portrait-card", fixture(), "x.jpg", "image/jpeg"), token(anonymous)), 401, "AUTHENTICATION_REQUIRED");
        assertError(upload(client, body("portrait-card", fixture(), "x.jpg", "image/jpeg"), "invalid"), 403, "CSRF_INVALID");
        var request = HttpRequest.newBuilder(URI.create(origin + "/assets")).header("X-CSRF-TOKEN", csrf)
                .header("Content-Type", "multipart/form-data").POST(HttpRequest.BodyPublishers.ofString("broken")).build();
        assertError(client.send(request, HttpResponse.BodyHandlers.ofString()), 400, "INVALID_REQUEST");
        var plain = HttpRequest.newBuilder(URI.create(origin + "/assets")).header("X-CSRF-TOKEN", csrf)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertError(client.send(plain, HttpResponse.BodyHandlers.ofString()), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    private HttpClient login(String username) throws Exception {
        var target = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var request = HttpRequest.newBuilder(URI.create(origin + "/auth/login")).header("X-CSRF-TOKEN", token(target))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=" + username + "&password=test-only")).build();
        assertThat(target.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        return target;
    }

    private String token(HttpClient target) throws Exception {
        var response = target.send(HttpRequest.newBuilder(URI.create(origin + "/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body()).get("token").asString();
    }

    private HttpResponse<byte[]> get(HttpClient target, String path) throws Exception {
        return target.send(HttpRequest.newBuilder(URI.create(origin.replace("/api/v1", "") + path)).header("Accept", "image/*").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> upload(HttpClient target, byte[] bytes, String token) throws Exception {
        return target.send(HttpRequest.newBuilder(URI.create(origin + "/assets")).header("X-CSRF-TOKEN", token)
                .header("Content-Type", "multipart/form-data; boundary=frameflow-boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private byte[] body(String template, byte[] bytes, String filename, String type) throws Exception {
        return multipart(new String[]{"templateId", "file"}, new byte[][]{template.getBytes(StandardCharsets.UTF_8), bytes},
                new String[]{null, filename}, new String[]{null, type});
    }

    private byte[] multipart(String[] names, byte[][] bytes, String[] files, String[] types) throws Exception {
        var result = new ByteArrayOutputStream();
        for (int i = 0; i < names.length; i++) {
            String header = "--frameflow-boundary\r\nContent-Disposition: form-data; name=\"" + names[i] + "\""
                    + (files[i] == null ? "" : "; filename=\"" + files[i] + "\"") + "\r\n"
                    + (types[i] == null ? "" : "Content-Type: " + types[i] + "\r\n") + "\r\n";
            result.write(header.getBytes(StandardCharsets.UTF_8));
            result.write(bytes[i]); result.write("\r\n".getBytes());
        }
        result.write("--frameflow-boundary--\r\n".getBytes());
        return result.toByteArray();
    }

    private byte[] fixture() throws Exception { return new org.springframework.core.io.ClassPathResource("demo-media/mock-result.jpg").getContentAsByteArray(); }

    private void assertError(HttpResponse<?> response, int status, String code) throws Exception {
        String body = response.body() instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : response.body().toString();
        assertThat(response.statusCode()).as(body).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        var json = mapper.readTree(body);
        assertThat(json.get("error").get("code").asString()).isEqualTo(code);
        assertThat(json.get("error").get("details").isArray()).isTrue();
        assertThat(json.get("requestId").asString()).isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(body).doesNotContain("Exception", root.toString(), "private SQL", "password");
    }
}
