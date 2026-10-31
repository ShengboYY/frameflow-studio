package com.frameflow.studio.trigger.http.task;

import java.io.ByteArrayInputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import com.frameflow.studio.cases.asset.model.AssetOperationException;
import com.frameflow.studio.cases.asset.model.MediaResource;
import com.frameflow.studio.cases.identity.IAuthenticateAccountCase;
import com.frameflow.studio.cases.identity.model.AccountView;
import com.frameflow.studio.cases.task.ICreateProductionTaskCase;
import com.frameflow.studio.cases.task.IQueryProductionTaskCase;
import com.frameflow.studio.cases.task.IReadTaskResultCase;
import com.frameflow.studio.cases.task.model.ExecutionOptions;
import com.frameflow.studio.cases.task.model.TaskAccepted;
import com.frameflow.studio.cases.task.model.TaskOperationException;
import com.frameflow.studio.cases.task.model.TaskView;
import com.frameflow.studio.cases.template.model.TemplateValidation;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.session.cookie.secure=false", "app.tasks.mock-controls-enabled=true"})
class TaskApiTest {
    @Autowired Environment environment;
    @Autowired ObjectMapper mapper;
    @MockitoBean IAuthenticateAccountCase authenticate;
    @MockitoBean ICreateProductionTaskCase create;
    @MockitoBean IQueryProductionTaskCase query;
    @MockitoBean IReadTaskResultCase result;
    final UUID owner = UUID.randomUUID();
    final UUID id = UUID.randomUUID();
    final UUID asset = UUID.randomUUID();
    final Instant now = Instant.parse("2026-10-31T01:02:03.123456Z");
    HttpClient client;
    String origin;
    String csrf;

    @BeforeEach
    void prepare() throws Exception {
        origin = "http://127.0.0.1:" + environment.getProperty("local.server.port");
        when(authenticate.authenticate(eq("demo-a"), anyString())).thenReturn(new AccountView(owner, "demo-a", "演示用户 A"));
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var login = client.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded").header("X-CSRF-TOKEN", token(client))
                .POST(HttpRequest.BodyPublishers.ofString("username=demo-a&password=test-only")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        csrf = token(client);
        when(create.create(any(), any(), any(), any())).thenReturn(new TaskAccepted(id, owner, now));
    }

    @Test
    void acceptedHasOnlyIdentityAndTimeAndLocationAndCapturesIndependentOptions() throws Exception {
        var accepted = post(client, valid(), csrf, "application/json");
        assertThat(accepted.statusCode()).isEqualTo(202);
        assertThat(accepted.headers().firstValue("Location")).contains("/api/v1/tasks/" + id);
        var body = mapper.readTree(accepted.body());
        assertThat(body.size()).isEqualTo(3);
        assertThat(body.get("ownerId").asString()).isEqualTo(owner.toString());
        assertThat(body.get("createdAt").asString()).isEqualTo(now.toString());
        verify(create).create(owner, "portrait-card", List.of(asset), new ExecutionOptions(null, 1000));
        var controlled = valid().replace("}", ",\"simulation\":{\"failAtStage\":\"GENERATE\",\"stageDelayMs\":17}}");
        assertThat(post(client, controlled, csrf, "application/json").statusCode()).isEqualTo(202);
        verify(create).create(owner, "portrait-card", List.of(asset), ExecutionOptions.from("GENERATE", 17));
        assertThat(post(client, valid().replace("}", ",\"simulation\":{}}"), csrf, "application/json").statusCode()).isEqualTo(202);
        verify(create, times(2)).create(owner, "portrait-card", List.of(asset), new ExecutionOptions(null, 1000));
    }

    @Test
    void strictJsonRejectsUnknownDuplicateMissingCoercedAndTrailingValuesBeforeCase() throws Exception {
        for (String invalid : List.of("", "null", "[]", "{}", valid() + "{}",
                valid().replace("}", ",\"ownerId\":\"" + owner + "\"}"),
                valid().replace("}", ",\"templateId\":\"portrait-card\"}"),
                valid().replace("\"portrait-card\"", "17"),
                valid().replace(asset.toString(), asset.toString().toUpperCase()),
                valid().replace("[\"" + asset + "\"]", "[null]"),
                valid().replace("[\"" + asset + "\"]", "\"" + asset + "\""),
                valid().replace("}", ",\"simulation\":null}"),
                valid().replace("}", ",\"simulation\":{\"extra\":1}}"),
                valid().replace("}", ",\"simulation\":{\"stageDelayMs\":1,\"stageDelayMs\":2}}"))) {
            assertError(post(client, invalid, csrf, "application/json"), 400, "INVALID_REQUEST");
        }
        for (String invalid : List.of("{\"stageDelayMs\":null}", "{\"stageDelayMs\":1.5}", "{\"stageDelayMs\":-1}",
                "{\"stageDelayMs\":30001}", "{\"stageDelayMs\":\"1\"}", "{\"stageDelayMs\":999999999999}",
                "{\"failAtStage\":1}", "{\"failAtStage\":\"NONE\"}")) {
            assertError(post(client, valid().replace("}", ",\"simulation\":" + invalid + "}"), csrf, "application/json"),
                    400, "INVALID_SIMULATION");
        }
        verifyNoInteractions(create);
    }

    @Test
    void bodyLimitIsInclusiveForBothKnownLengthAndChunkedRequests() throws Exception {
        var exactly = valid() + " ".repeat(65536 - valid().getBytes(StandardCharsets.UTF_8).length);
        assertThat(post(client, exactly, csrf, "application/json").statusCode()).isEqualTo(202);
        assertError(post(client, exactly + " ", csrf, "application/json"), 413, "PAYLOAD_TOO_LARGE");
        for (boolean tooLarge : new boolean[]{false, true}) {
            byte[] bytes = (exactly + (tooLarge ? " " : "")).getBytes(StandardCharsets.UTF_8);
            var request = HttpRequest.newBuilder(URI.create(origin + "/api/v1/tasks")).header("X-CSRF-TOKEN", csrf)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (tooLarge) assertError(response, 413, "PAYLOAD_TOO_LARGE");
            else assertThat(response.statusCode()).isEqualTo(202);
        }
        verify(create, times(2)).create(any(), any(), any(), any());
    }

    @Test
    void disabledControlsAreRejectedAndOmittedControlsStillUseDefaults() throws Exception {
        var reader = new TaskSubmissionReader(mapper, false);
        for (String simulation : List.of("{}", "{\"failAtStage\":null}", "{\"stageDelayMs\":0}")) {
            var request = new MockHttpServletRequest();
            request.setContent(valid().replace("}", ",\"simulation\":" + simulation + "}").getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> reader.read(request)).isInstanceOfSatisfying(TaskSubmissionReader.InvalidRequest.class,
                    failure -> assertThat(failure.code()).isEqualTo("SIMULATION_CONTROLS_DISABLED"));
        }
        var request = new MockHttpServletRequest();
        request.setContent(valid().getBytes(StandardCharsets.UTF_8));
        assertThat(reader.options(reader.read(request))).isEqualTo(new ExecutionOptions(null, 1000));
    }

    @Test
    void authCsrfMediaTypeAndUntrustedQueryParametersAreEnforced() throws Exception {
        var anonymous = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        assertError(post(anonymous, valid(), token(anonymous), "application/json"), 401, "AUTHENTICATION_REQUIRED");
        assertError(get(anonymous, "/api/v1/tasks"), 401, "AUTHENTICATION_REQUIRED");
        assertError(post(client, valid(), "bad", "application/json"), 403, "CSRF_INVALID");
        assertError(post(client, valid(), csrf, "text/plain"), 415, "UNSUPPORTED_MEDIA_TYPE");
        for (String path : List.of("/api/v1/tasks?ownerId=" + owner, "/api/v1/tasks/invalid", "/api/v1/tasks/" + id + "?ownerId=" + owner,
                "/api/v1/tasks/" + id + "/result?ownerId=" + owner)) assertError(get(client, path), 400, "INVALID_REQUEST");
        var withQuery = HttpRequest.newBuilder(URI.create(origin + "/api/v1/tasks?ownerId=" + owner)).header("X-CSRF-TOKEN", csrf)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(valid())).build();
        assertError(client.send(withQuery, HttpResponse.BodyHandlers.ofString()), 400, "INVALID_REQUEST");
        verifyNoInteractions(create, query, result);
    }

    @Test
    void mapsAllTaskViewCombinationsAndFailedReadsAre200WithoutChangingFacts() throws Exception {
        var views = List.of(view("PENDING", null, 0, null, null), view("RUNNING", "CHECK_ASSETS", 1, null, null),
                view("RUNNING", "GENERATE", 2, null, null), view("RUNNING", "FINALIZE", 3, null, null),
                view("SUCCEEDED", "FINALIZE", 4, new TaskView.Result("image/jpeg"), null),
                view("FAILED", "GENERATE", 3, null, new TaskView.Failure("STAGE", "GENERATE", "MOCK_STAGE_FAILURE", "该阶段模拟失败")),
                view("FAILED", null, 1, null, new TaskView.Failure("DISPATCH", null, "DISPATCH_REJECTED", "任务队列繁忙")));
        when(query.list(owner)).thenReturn(views);
        var history = get(client, "/api/v1/tasks");
        assertThat(history.statusCode()).isEqualTo(200);
        var items = mapper.readTree(history.body()).get("items");
        assertThat(items.size()).isEqualTo(7);
        for (int i = 0; i < views.size(); i++) {
            when(query.find(owner, id)).thenReturn(views.get(i));
            var single = get(client, "/api/v1/tasks/" + id);
            assertThat(single.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(single.body())).isEqualTo(items.get(i));
            assertThat(items.get(i).size()).isEqualTo(10);
            assertThat(items.get(i).get("revision").asLong()).isEqualTo(views.get(i).revision());
            assertThat(items.get(i).get("createdAt").asString()).endsWith("Z");
        }
        assertThat(items.get(4).get("result").get("previewUrl").asString()).isEqualTo("/api/v1/tasks/" + id + "/result");
        assertThat(items.get(0).get("currentStage").isNull()).isTrue();
        assertThat(items.get(0).get("result").isNull()).isTrue();
        assertThat(items.get(0).get("failure").isNull()).isTrue();
        assertThat(items.get(6).get("failure").get("stage").isNull()).isTrue();
        when(query.list(owner)).thenReturn(List.of());
        assertThat(mapper.readTree(get(client, "/api/v1/tasks").body()).get("items").size()).isZero();
        verifyNoInteractions(create, result);
    }

    @Test
    void submissionErrorsAndImagesUseStructuredSanitizedErrorsEvenWhenAcceptIsImage() throws Exception {
        doThrow(new TaskOperationException(TaskOperationException.Reason.SUBMISSION_OUTCOME_UNKNOWN, id, List.of()))
                .when(create).create(any(), any(), any(), any());
        var unknown = post(client, valid(), csrf, "application/json");
        assertError(unknown, 503, "SUBMISSION_OUTCOME_UNKNOWN");
        assertThat(mapper.readTree(unknown.body()).get("error").get("taskId").asString()).isEqualTo(id.toString());
        doThrow(new TaskOperationException(TaskOperationException.Reason.REQUIREMENTS_MISMATCH, null,
                List.of(TemplateValidation.Violation.IMAGE_COUNT))).when(create).create(any(), any(), any(), any());
        var mismatch = post(client, valid(), csrf, "application/json");
        assertError(mismatch, 422, "TEMPLATE_REQUIREMENTS_MISMATCH");
        assertThat(mapper.readTree(mismatch.body()).get("error").has("taskId")).isFalse();
        assertThat(mapper.readTree(mismatch.body()).get("error").get("details").get(0).get("code").asString()).isEqualTo("COUNT_MISMATCH");
        doThrow(new TaskOperationException(TaskOperationException.Reason.NOT_FOUND)).when(result).read(owner, id);
        assertError(get(client, "/api/v1/tasks/" + id + "/result"), 404, "RESOURCE_NOT_FOUND");
        doThrow(new TaskOperationException(TaskOperationException.Reason.RESULT_NOT_READY)).when(result).read(owner, id);
        assertError(get(client, "/api/v1/tasks/" + id + "/result"), 409, "RESULT_NOT_READY");
        doThrow(new AssetOperationException(AssetOperationException.Reason.UNAVAILABLE)).when(result).read(owner, id);
        assertError(get(client, "/api/v1/tasks/" + id + "/result"), 503, "RESOURCE_UNAVAILABLE");
        byte[] bytes = new org.springframework.core.io.ClassPathResource("demo-media/mock-result.jpg").getContentAsByteArray();
        doReturn(new MediaResource("demo:mock-result", "image/jpeg", bytes)).when(result).read(owner, id);
        var image = client.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/tasks/" + id + "/result")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.body()).isEqualTo(bytes);
        assertThat(image.headers().firstValue("Content-Type")).contains("image/jpeg");
        assertThat(image.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(image.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(image.headers().firstValue("Content-Disposition")).isEmpty();
    }

    private TaskView view(String status, String stage, long revision, TaskView.Result image, TaskView.Failure failure) {
        return new TaskView(id, owner, "portrait-card", status, stage, revision, now, now, image, failure);
    }
    private String valid() { return "{\"templateId\":\"portrait-card\",\"assetIds\":[\"" + asset + "\"]}"; }
    private HttpResponse<String> post(HttpClient target, String body, String token, String type) throws Exception {
        return target.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/tasks")).header("Content-Type", type)
                .header("X-CSRF-TOKEN", token).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(HttpClient target, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin + path));
        if (path.endsWith("/result")) request.header("Accept", "image/*");
        return target.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private String token(HttpClient target) throws Exception {
        var response = get(target, "/api/v1/auth/csrf");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return mapper.readTree(response.body()).get("token").asString();
    }
    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        var body = mapper.readTree(response.body());
        assertThat(body.get("error").get("code").asString()).isEqualTo(code);
        assertThat(body.get("error").get("details").isArray()).isTrue();
        assertThat(body.get("requestId").asString()).isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.body()).doesNotContain("Exception", "password", "demo:");
    }
}
