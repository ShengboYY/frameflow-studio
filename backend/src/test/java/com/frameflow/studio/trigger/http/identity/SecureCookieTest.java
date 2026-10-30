package com.frameflow.studio.trigger.http.identity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecureCookieTest {
    @Autowired Environment environment;

    @Test
    void secureCookieIsDefaultEvenWhenInternalHopUsesHttp() throws Exception {
        String origin = "http://127.0.0.1:" + environment.getProperty("local.server.port");
        var result = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/auth/csrf"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.headers().firstValue("set-cookie").orElseThrow())
                .contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/api").doesNotContain("Domain=");
    }
}
