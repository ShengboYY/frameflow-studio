package com.frameflow.studio.trigger.http.template;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class TemplateApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Environment environment;

    @Test
    void publicCatalogSupportsCategoriesFilteringAndExactDetailContracts() throws Exception {
        mvc.perform(get("/api/v1/templates/categories")).andExpect(status().isOk())
                .andExpect(jsonPath("items.length()").value(2))
                .andExpect(jsonPath("items[0].categoryId").value("creative"))
                .andExpect(jsonPath("items[0].name").value("创意图片"))
                .andExpect(jsonPath("items[1].categoryId").value("travel"));
        var all = mvc.perform(get("/api/v1/templates")).andExpect(status().isOk())
                .andExpect(jsonPath("items[0].templateId").value("portrait-card"))
                .andExpect(jsonPath("items[1].templateId").value("travel-postcard")).andReturn();
        var items = mapper.readTree(all.getResponse().getContentAsString()).get("items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).size()).isEqualTo(4);
        mvc.perform(get("/api/v1/templates").param("categoryId", "creative"))
                .andExpect(status().isOk()).andExpect(jsonPath("items.length()").value(1))
                .andExpect(jsonPath("items[0].templateId").value("portrait-card"));
        mvc.perform(get("/api/v1/templates").param("categoryId", "travel"))
                .andExpect(status().isOk()).andExpect(jsonPath("items[0].templateId").value("travel-postcard"));
        mvc.perform(get("/api/v1/templates").param("categoryId", "unknown"))
                .andExpect(status().isOk()).andExpect(jsonPath("items").isEmpty());
        for (int i = 0; i < items.size(); i++) {
            String id = items.get(i).get("templateId").asString();
            mvc.perform(get("/api/v1/templates/" + id)).andExpect(status().isOk())
                    .andExpect(jsonPath("templateId").value(id))
                    .andExpect(jsonPath("description").isNotEmpty())
                    .andExpect(jsonPath("coverUrl").value(items.get(i).get("coverUrl").asString()))
                    .andExpect(jsonPath("requirements.requiredImageCount").value(i + 1))
                    .andExpect(jsonPath("requirements.acceptedMimeTypes[0]").value("image/jpeg"))
                    .andExpect(jsonPath("requirements.acceptedMimeTypes[1]").value("image/png"))
                    .andExpect(jsonPath("requirements.maxFileSizeBytes").value(i == 0 ? 5242880 : 8388608));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/templates?owner=x", "/templates?categoryId=travel&categoryId=creative",
            "/templates?categoryId=", "/templates?categoryId=Travel", "/templates?categoryId=../private",
            "/templates/categories?categoryId=travel", "/templates/portrait-card?extra=x",
            "/templates/INVALID", "/demo-media/portrait-cover?path=application.yml",
            "/demo-media/portrait-cover.png"})
    void invalidIdsAndUnknownOrRepeatedQueryParametersReturnJson400(String suffix) throws Exception {
        assertError(suffix, 400, "INVALID_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/templates/absent", "/demo-media/absent", "/demo-media/mock-result",
            "/demo-media/application-yml"})
    void unknownTemplatesCoversAndInternalResultAreJson404EvenWhenAcceptingImages(String suffix) throws Exception {
        assertError(suffix, 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    void realHttpCanReadBothCoversAndCannotReadFixtureOrClasspathFiles() throws Exception {
        String origin = "http://127.0.0.1:" + environment.getProperty("local.server.port");
        var client = HttpClient.newHttpClient();
        for (String key : new String[]{"portrait-cover", "postcard-cover"}) {
            var response = client.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/demo-media/" + key))
                    .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type")).contains("image/png");
            assertThat(response.headers().firstValue("Content-Length")).contains(String.valueOf(response.body().length));
            assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
            var image = ImageIO.read(new ByteArrayInputStream(response.body()));
            assertThat(image.getWidth()).isEqualTo(640);
            assertThat(image.getHeight()).isEqualTo(400);
        }
        for (String path : new String[]{"mock-result", "..%2Fapplication.yml", "%2e%2e/application.yml",
                "portrait-cover%2F..%2Fmock-result", "demo-media/mock-result.png"}) {
            var response = client.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/demo-media/" + path))
                    .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).as(path).isIn(400, 401, 404);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).doesNotStartWith("image/");
            assertThat(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8))
                    .doesNotContain("datasource:", "mock-result.png");
        }
    }

    private void assertError(String suffix, int status, String code) throws Exception {
        var response = mvc.perform(get("/api/v1" + suffix).accept("image/png"))
                .andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("error.code").value(code)).andExpect(jsonPath("error.details").isArray())
                .andReturn().getResponse();
        var body = mapper.readTree(response.getContentAsString());
        assertThat(UUID.fromString(body.get("requestId").asString()).toString())
                .isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(response.getContentAsString()).doesNotContain("Exception", "classpath", "password");
    }
}
