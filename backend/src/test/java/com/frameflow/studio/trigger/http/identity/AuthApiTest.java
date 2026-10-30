package com.frameflow.studio.trigger.http.identity;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import com.frameflow.studio.domain.identity.adapter.repository.IAccountRepository;
import com.frameflow.studio.domain.identity.model.entity.Account;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthApiTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final String PASSWORD = " untouched 密码 ";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired Environment environment;
    @MockitoBean IAccountRepository repository;
    @MockitoBean Clock clock;
    private Account account;

    @BeforeAll
    void account() {
        account = new Account(UUID.randomUUID(), "demo-a", "演示用户 A", encoder.encode(PASSWORD), NOW);
    }

    @BeforeEach
    void prepare() {
        when(clock.instant()).thenReturn(NOW);
        when(repository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(repository.findByUsername("demo-a")).thenReturn(Optional.of(account));
        when(repository.findById(account.getAccountId())).thenReturn(Optional.of(account));
    }

    @Test
    void loginRotatesSessionRestoresIdentityAndInvalidatesOldCsrf() throws Exception {
        Token before = token(null);
        String originalId = before.session.getId();
        MvcResult login = login(before, form("demo-a", PASSWORD), "application/x-www-form-urlencoded");
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(login.getResponse().getContentAsString()).doesNotContain("password", "hash", "sessionId");
        assertThat(before.session.getId()).isNotEqualTo(originalId);
        mvc.perform(get("/api/v1/auth/session").session(before.session))
                .andExpect(status().isOk()).andExpect(jsonPath("ownerId").value(account.getAccountId().toString()));
        mvc.perform(post("/api/v1/auth/logout").session(before.session).header("X-CSRF-TOKEN", before.value))
                .andExpect(status().isForbidden()).andExpect(jsonPath("error.code").value("CSRF_INVALID"));
        Token current = token(before.session);
        mvc.perform(post("/api/v1/auth/login").session(current.session).header("X-CSRF-TOKEN", current.value)
                        .contentType("application/x-www-form-urlencoded").content(form("demo-a", PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("error.code").value("ALREADY_AUTHENTICATED"));
    }

    @Test
    void logoutInvalidatesServerSessionClearsCorrectCookieAndRejectsOldTokenInNewSession() throws Exception {
        Token anonymous = token(null);
        login(anonymous, form("demo-a", PASSWORD), "application/x-www-form-urlencoded");
        Token authenticated = token(anonymous.session);
        mvc.perform(post("/api/v1/auth/logout").session(authenticated.session)
                        .header("X-CSRF-TOKEN", authenticated.value))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Path=/api")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        assertThat(authenticated.session.isInvalid()).isTrue();
        Token next = token(null);
        mvc.perform(post("/api/v1/auth/login").session(next.session).header("X-CSRF-TOKEN", authenticated.value)
                        .contentType("application/x-www-form-urlencoded").content(form("demo-a", PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").session(next.session).header("X-CSRF-TOKEN", next.value))
                .andExpect(status().isNoContent());
    }

    @Test
    void allPostsRequireCsrfBeforeBusinessHandling() throws Exception {
        for (String path : new String[]{"auth/login", "auth/logout", "assets", "tasks"}) {
            mvc.perform(post("/api/v1/" + path)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("error.code").value("CSRF_INVALID"));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void csrfCannotBePassedThroughQueryOrFormParameters() throws Exception {
        Token token = token(null);
        mvc.perform(post("/api/v1/tasks").session(token.session).param("_csrf", token.value))
                .andExpect(status().isForbidden()).andExpect(jsonPath("error.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/v1/auth/login").session(token.session).param("_csrf", token.value)
                        .contentType("application/x-www-form-urlencoded").content(form("demo-a", PASSWORD)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void unknownAndIncorrectCredentialsUseSameJsonErrorAndFreshRequestIds() throws Exception {
        Token token = token(null);
        for (String body : new String[]{form("demo-a", "wrong"), form("absent.account", "wrong")}) {
            var result = login(token, body, "application/x-www-form-urlencoded");
            assertError(result, 401, "INVALID_CREDENTIALS");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"username=demo-a", "username=demo-a&password=", "username=demo-a&password=a&ownerId=x",
            "username=demo-a&username=demo-b&password=a", "username=demo-a&password=a&password=b",
            "username=Demo-a&password=a", "username=demo-a&password=%GG", "username=demo-a&password=%C3%28",
            "username=demo-a&password=%E4%B8", "username=demo-a&password=a&", "username=demo-a&password=a&=x"})
    void malformedDuplicateAndUnknownFieldsAreRejected(String body) throws Exception {
        assertError(login(token(null), body, "application/x-www-form-urlencoded"), 400, "INVALID_REQUEST");
        verifyNoInteractions(repository);
    }

    @Test
    void utf8LoginPasswordByteLimitsAndNoTrimAreEnforced() throws Exception {
        assertError(login(token(null), form("demo-a", "中".repeat(25)), "application/x-www-form-urlencoded"),
                400, "INVALID_REQUEST");
        assertError(login(token(null), form("demo-a", "中".repeat(24)), "application/x-www-form-urlencoded"),
                401, "INVALID_CREDENTIALS");
        assertError(login(token(null), form("demo-a", PASSWORD.trim()), "application/x-www-form-urlencoded"),
                401, "INVALID_CREDENTIALS");
        assertThat(login(token(null), form("demo-a", PASSWORD), "application/x-www-form-urlencoded").getResponse().getStatus())
                .isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/plain", "application/*", "application/x-www-form-urlencoded;charset=ISO-8859-1",
            "application/x-www-form-urlencoded;charset=made-up", "application/x-www-form-urlencoded;extra=true"})
    void unsupportedMediaOrEncodingIs415(String contentType) throws Exception {
        assertError(login(token(null), form("demo-a", PASSWORD), contentType), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void queryFieldsAreRejectedAndOversizedBodyIsBounded() throws Exception {
        Token token = token(null);
        mvc.perform(post("/api/v1/auth/login?username=demo-a").session(token.session)
                        .header("X-CSRF-TOKEN", token.value).contentType("application/x-www-form-urlencoded")
                        .content(form("demo-a", PASSWORD))).andExpect(status().isBadRequest());
        assertError(login(token, "username=demo-a&password=" + "x".repeat(4096),
                "application/x-www-form-urlencoded"), 413, "PAYLOAD_TOO_LARGE");
    }

    @Test
    void missingSessionAndDatabaseOutagesHaveDifferentStatusAndDoNotDestroyValidSession() throws Exception {
        assertError(mvc.perform(get("/api/v1/auth/session")).andReturn(), 401, "AUTHENTICATION_REQUIRED");
        Token anonymous = token(null);
        when(repository.findByUsername("demo-a")).thenThrow(new DataAccessResourceFailureException("private SQL"));
        assertError(login(anonymous, form("demo-a", PASSWORD), "application/x-www-form-urlencoded"),
                503, "SERVICE_UNAVAILABLE");
        doReturn(Optional.of(account)).when(repository).findByUsername("demo-a");
        login(anonymous, form("demo-a", PASSWORD), "application/x-www-form-urlencoded");
        when(repository.findById(account.getAccountId())).thenThrow(new DataAccessResourceFailureException("private SQL"));
        assertError(mvc.perform(get("/api/v1/auth/session").session(anonymous.session)).andReturn(),
                503, "SERVICE_UNAVAILABLE");
        assertThat(anonymous.session.isInvalid()).isFalse();
        doReturn(Optional.of(account)).when(repository).findById(account.getAccountId());
        mvc.perform(get("/api/v1/auth/session").session(anonymous.session)).andExpect(status().isOk());
    }

    @Test
    void deletedAccountInvalidatesSession() throws Exception {
        Token anonymous = token(null);
        login(anonymous, form("demo-a", PASSWORD), "application/x-www-form-urlencoded");
        when(repository.findById(account.getAccountId())).thenReturn(Optional.empty());
        assertError(mvc.perform(get("/api/v1/auth/session").session(anonymous.session)).andReturn(),
                401, "AUTHENTICATION_REQUIRED");
        assertThat(anonymous.session.isInvalid()).isTrue();
    }

    @Test
    void idleTimeoutIsEnforcedAtThirtyMinutesWithControlledTime() throws Exception {
        Token token = token(null);
        login(token, form("demo-a", PASSWORD), "application/x-www-form-urlencoded");
        when(clock.instant()).thenReturn(NOW.plusSeconds(1799));
        mvc.perform(get("/api/v1/auth/session").session(token.session)).andExpect(status().isOk());
        when(clock.instant()).thenReturn(NOW.plusSeconds(3599));
        assertError(mvc.perform(get("/api/v1/auth/session").session(token.session)).andReturn(),
                401, "AUTHENTICATION_REQUIRED");
        assertThat(token.session.isInvalid()).isTrue();
    }

    @Test
    void absoluteTimeoutAppliesEvenWithRecentActivityAndOldPostTokenIsRejected() throws Exception {
        Token token = token(null);
        login(token, form("demo-a", PASSWORD), "application/x-www-form-urlencoded");
        Token authenticated = token(token.session);
        authenticated.session.setAttribute(SessionLifetimeFilter.LAST_ACTIVITY, NOW.plusSeconds(28799));
        when(clock.instant()).thenReturn(NOW.plusSeconds(28800));
        mvc.perform(post("/api/v1/auth/logout").session(authenticated.session)
                        .header("X-CSRF-TOKEN", authenticated.value)).andExpect(status().isForbidden());
        assertThat(authenticated.session.isInvalid()).isTrue();
    }

    @Test
    void noDefaultHtmlLoginOrBasicAuthenticationAndPublicResourceRoutesStayPublic() throws Exception {
        mvc.perform(get("/api/v1/auth/login")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/login")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/templates")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/demo-media/example")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tasks").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void realHttpCookieFlowHasRequiredAttributesAndRotation() throws Exception {
        String origin = "http://127.0.0.1:" + environment.getProperty("local.server.port");
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        var csrf = client.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String cookie = csrf.headers().firstValue("set-cookie").orElseThrow();
        assertThat(cookie).contains("FF_SESSION=", "Path=/api", "HttpOnly", "SameSite=Lax", "Max-Age=28800")
                .doesNotContain("Domain=", "Secure");
        String anonymousId = cookies.getCookieStore().getCookies().get(0).getValue();
        String token = mapper.readTree(csrf.body()).get("token").asString();
        var login = client.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/auth/login"))
                        .header("X-CSRF-TOKEN", token).header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(form("demo-a", PASSWORD))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        assertThat(cookies.getCookieStore().getCookies().get(0).getValue()).isNotEqualTo(anonymousId);
        var session = client.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/auth/session")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(session.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(session.body()).get("ownerId").asString()).isEqualTo(account.getAccountId().toString());
        var fresh = client.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        var logout = client.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/auth/logout"))
                        .header("X-CSRF-TOKEN", mapper.readTree(fresh.body()).get("token").asString())
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.headers().allValues("set-cookie")).anySatisfy(value ->
                assertThat(value).contains("FF_SESSION=", "Path=/api", "Max-Age=0", "HttpOnly", "SameSite=Lax"));
        assertThat(cookies.getCookieStore().getCookies()).isEmpty();
        var expired = client.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/auth/session")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(expired.statusCode()).isEqualTo(401);
    }

    private Token token(MockHttpSession session) throws Exception {
        var request = get("/api/v1/auth/csrf");
        if (session != null) request.session(session);
        var result = mvc.perform(request).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        return new Token((MockHttpSession) result.getRequest().getSession(),
                mapper.readTree(result.getResponse().getContentAsString()).get("token").asString());
    }

    private MvcResult login(Token token, String body, String contentType) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").session(token.session)
                .with(request -> { request.setRequestedSessionId(token.session.getId()); return request; })
                .header("X-CSRF-TOKEN", token.value)
                .contentType(contentType).content(body)).andReturn();
    }

    private String form(String username, String password) {
        return "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        var response = result.getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        var body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("error").get("code").asString()).isEqualTo(code);
        assertThat(body.get("error").get("details").isArray()).isTrue();
        assertThat(body.get("requestId").asString()).isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(UUID.fromString(body.get("requestId").asString())).isNotNull();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString()).doesNotContain("private SQL", "passwordHash", "Exception");
    }

    private record Token(MockHttpSession session, String value) {}
}
