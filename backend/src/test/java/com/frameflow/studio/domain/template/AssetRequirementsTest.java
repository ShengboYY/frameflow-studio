package com.frameflow.studio.domain.template;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.frameflow.studio.domain.template.model.valobj.AssetRequirements;
import com.frameflow.studio.domain.template.model.valobj.AssetRequirements.ImageDescriptor;

import static com.frameflow.studio.domain.template.model.valobj.AssetRequirements.Violation.*;
import static org.assertj.core.api.Assertions.*;

class AssetRequirementsTest {
    @ParameterizedTest
    @ValueSource(longs = {5242880, 8388608})
    void sizeLimitIsInclusiveAndEmptyImagesAreRejected(long limit) {
        var rules = new AssetRequirements(1, List.of("image/jpeg", "image/png"), limit);
        for (String mime : rules.acceptedMimeTypes()) {
            for (long bytes : new long[]{1, limit - 1, limit}) {
                assertThat(rules.validateImage(new ImageDescriptor(mime, bytes))).isEmpty();
            }
            for (long bytes : new long[]{-1, 0, limit + 1, Long.MAX_VALUE}) {
                assertThat(rules.validateImage(new ImageDescriptor(mime, bytes))).containsExactly(IMAGE_SIZE);
            }
        }
        assertThat(rules.validateImage(new ImageDescriptor("image/gif", limit + 1)))
                .containsExactly(IMAGE_TYPE, IMAGE_SIZE);
        assertThat(rules.validateImage(new ImageDescriptor(null, 1))).containsExactly(IMAGE_TYPE);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void completeSubmissionCountsPositionsAndSingleImageValidationDoesNotCheckTotal(int required) {
        var rules = new AssetRequirements(required, List.of("image/png"), 100);
        var image = new ImageDescriptor("image/png", 100);
        assertThat(rules.validateImage(image)).isEmpty();
        assertThat(rules.validateSubmission(java.util.Collections.nCopies(required, image))).isEmpty();
        assertThat(rules.validateSubmission(java.util.Collections.nCopies(required - 1, image)))
                .containsExactly(IMAGE_COUNT);
        assertThat(rules.validateSubmission(java.util.Collections.nCopies(required + 1, image)))
                .containsExactly(IMAGE_COUNT);
        assertThat(rules.validateSubmission(List.of(new ImageDescriptor("image/jpeg", 101))))
                .contains(IMAGE_TYPE, IMAGE_SIZE);
    }

    @Test
    void invalidSeedRulesCannotBeConstructedAndMimeListIsImmutable() {
        for (int count : new int[]{-1, 0}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new AssetRequirements(count, List.of("image/png"), 1));
        }
        for (long size : new long[]{-1, 0, 8388609}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new AssetRequirements(1, List.of("image/png"), size));
        }
        for (var types : List.of(List.<String>of(), List.of("image/gif"), List.of("image/png", "image/png"))) {
            assertThatIllegalArgumentException().isThrownBy(() -> new AssetRequirements(1, types, 1));
        }
        var input = new ArrayList<>(List.of("image/png"));
        var rules = new AssetRequirements(1, input, 1);
        input.clear();
        assertThat(rules.acceptedMimeTypes()).containsExactly("image/png");
        assertThatThrownBy(() -> rules.acceptedMimeTypes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
