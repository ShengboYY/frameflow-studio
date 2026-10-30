package com.frameflow.studio.domain.task.model.valobj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TaskValuesTest {

    private static final UUID ASSET_A = UUID.fromString("283def77-032c-4e18-9d95-34b435a0f2f1");
    private static final UUID ASSET_B = UUID.fromString("283def77-032c-4e18-9d95-34b435a0f2f2");

    @Test
    void inputDefensivelyCopiesAnOrderedListIncludingRepeatedPositions() {
        List<UUID> assets = new ArrayList<>(List.of(ASSET_A, ASSET_B, ASSET_A));
        TaskInput input = new TaskInput("portrait-card", assets);
        assets.clear();
        assertThat(input.assetIds()).containsExactly(ASSET_A, ASSET_B, ASSET_A);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> input.assetIds().set(0, ASSET_B));
        assertThat(input).isEqualTo(new TaskInput("portrait-card", List.of(ASSET_A, ASSET_B, ASSET_A)));
        assertThat(input).isNotEqualTo(new TaskInput("portrait-card", List.of(ASSET_A, ASSET_A, ASSET_B)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingTemplateAndResultReferences(String value) {
        Class<? extends RuntimeException> expected = value == null
                ? NullPointerException.class : IllegalArgumentException.class;
        assertThatExceptionOfType(expected).isThrownBy(() -> new TaskInput(value, List.of(ASSET_A)));
        assertThatExceptionOfType(expected).isThrownBy(() -> new TaskResult(value));
    }

    @Test
    void rejectsMissingEmptyAndIncompleteInputBindings() {
        assertThatNullPointerException().isThrownBy(() -> new TaskInput("portrait-card", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new TaskInput("portrait-card", List.of()));
        assertThatNullPointerException().isThrownBy(() -> new TaskInput("portrait-card", Arrays.asList(ASSET_A, null)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingFailureCodeOrReason(String value) {
        Class<? extends RuntimeException> expected = value == null
                ? NullPointerException.class : IllegalArgumentException.class;
        assertThatExceptionOfType(expected).isThrownBy(() -> new FailureInfo(FailureKind.STAGE, TaskStage.GENERATE, value, "生成失败"));
        assertThatExceptionOfType(expected).isThrownBy(() -> new FailureInfo(FailureKind.STAGE, TaskStage.GENERATE, "GENERATION_FAILED", value));
    }

    @ParameterizedTest
    @MethodSource("invalidFailureBoundaries")
    void rejectsMissingOrContradictoryFailureBoundaries(FailureKind kind, TaskStage stage) {
        Class<? extends RuntimeException> expected = kind == null
                ? NullPointerException.class : IllegalArgumentException.class;
        assertThatExceptionOfType(expected).isThrownBy(() -> new FailureInfo(kind, stage, "FAILURE", "执行失败"));
    }

    @Test
    void resultAndFailureHaveValueEquality() {
        assertThat(new TaskResult("demo-portrait")).isEqualTo(new TaskResult("demo-portrait"));
        assertThat(new FailureInfo(FailureKind.DISPATCH, null, "DISPATCH_REJECTED", "未能开始执行"))
                .isEqualTo(new FailureInfo(FailureKind.DISPATCH, null, "DISPATCH_REJECTED", "未能开始执行"));
    }

    private static Stream<Arguments> invalidFailureBoundaries() {
        return Stream.concat(
                Stream.of(Arguments.of(null, null), Arguments.of(FailureKind.STAGE, null)),
                Arrays.stream(TaskStage.values()).map(stage -> Arguments.of(FailureKind.DISPATCH, stage)));
    }
}
