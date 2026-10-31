package com.frameflow.studio.infrastructure.adapter.port.task;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.frameflow.studio.domain.task.model.valobj.TaskInput;
import com.frameflow.studio.infrastructure.adapter.port.asset.PackagedDemoMediaPort;

import static org.assertj.core.api.Assertions.*;

class MockMediaGeneratorPortTest {
    @Test
    void eachTemplateProducesItsOwnResultAndUnknownTemplatesNeverFallBackToAnotherDesign() {
        var generator = new MockMediaGeneratorPort();
        var portrait = generator.generate(new TaskInput("portrait-card", List.of(UUID.randomUUID())));
        var postcard = generator.generate(new TaskInput("travel-postcard", List.of(UUID.randomUUID(), UUID.randomUUID())));
        assertThat(portrait.reference()).isEqualTo("demo:portrait-result");
        assertThat(postcard.reference()).isEqualTo("demo:postcard-result");
        assertThatThrownBy(() -> generator.generate(new TaskInput("unknown", List.of(UUID.randomUUID()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatedReferencesResolveToTheMatchingPackagedArtwork() throws Exception {
        var generator = new MockMediaGeneratorPort();
        var media = new PackagedDemoMediaPort();
        for (var entry : java.util.Map.of("portrait-card", "portrait-result", "travel-postcard", "postcard-result").entrySet()) {
            var candidate = generator.generate(new TaskInput(entry.getKey(), List.of(UUID.randomUUID())));
            var image = media.findResult(candidate.reference()).orElseThrow();
            var expected = new org.springframework.core.io.ClassPathResource("demo-media/" + entry.getValue() + ".jpg");
            assertThat(image.bytes()).isEqualTo(expected.getContentAsByteArray());
            assertThat(media.findCover(entry.getValue())).isEmpty();
        }
    }
}
