package com.frameflow.studio.domain.task.model.aggregate;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.frameflow.studio.domain.task.model.valobj.FailureInfo;
import com.frameflow.studio.domain.task.model.valobj.FailureKind;
import com.frameflow.studio.domain.task.model.valobj.TaskInput;
import com.frameflow.studio.domain.task.model.valobj.TaskResult;
import com.frameflow.studio.domain.task.model.valobj.TaskStage;
import com.frameflow.studio.domain.task.model.valobj.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ProductionTaskTest {

    private static final UUID TASK_ID = UUID.fromString("283def77-032c-4e18-9d95-34b435a0f2f1");
    private static final UUID OWNER_ID = UUID.fromString("b92661af-8e57-4ac4-9aac-56640e063407");
    private static final Instant CREATED_AT = Instant.parse("2026-10-30T08:00:00Z");
    private static final TaskInput INPUT = new TaskInput("portrait-card", List.of(TASK_ID, OWNER_ID, TASK_ID));
    private static final TaskResult RESULT = new TaskResult("demo-portrait");
    private static final String CODE = "MOCK_STAGE_FAILURE";
    private static final String MESSAGE = "本阶段模拟失败";
    private static final FailureInfo DISPATCH_FAILURE =
            new FailureInfo(FailureKind.DISPATCH, null, "DISPATCH_REJECTED", "未能开始执行");

    // This is the nine-row specification table, independent of the implementation's guards.
    private static final List<Conclusion> LEGAL = List.of(
            new Conclusion(TaskStatus.PENDING, null, null, null),
            new Conclusion(TaskStatus.RUNNING, TaskStage.CHECK_ASSETS, null, null),
            new Conclusion(TaskStatus.RUNNING, TaskStage.GENERATE, null, null),
            new Conclusion(TaskStatus.RUNNING, TaskStage.FINALIZE, null, null),
            new Conclusion(TaskStatus.SUCCEEDED, TaskStage.FINALIZE, RESULT, null),
            new Conclusion(TaskStatus.FAILED, TaskStage.CHECK_ASSETS, null, stageFailure(TaskStage.CHECK_ASSETS)),
            new Conclusion(TaskStatus.FAILED, TaskStage.GENERATE, null, stageFailure(TaskStage.GENERATE)),
            new Conclusion(TaskStatus.FAILED, TaskStage.FINALIZE, null, stageFailure(TaskStage.FINALIZE)),
            new Conclusion(TaskStatus.FAILED, null, null, DISPATCH_FAILURE));

    private static final List<Edge> EDGES = List.of(
            new Edge(0, Action.START, 1),
            new Edge(1, Action.COMPLETE_CHECK, 2),
            new Edge(2, Action.COMPLETE_GENERATION, 3),
            new Edge(3, Action.SUCCEED, 4),
            new Edge(1, Action.FAIL_CHECK, 5),
            new Edge(2, Action.FAIL_GENERATION, 6),
            new Edge(3, Action.FAIL_FINALIZE, 7),
            new Edge(0, Action.FAIL_DISPATCH, 8));

    @Test
    void acceptsFixedInputAndFollowsTheNormalChainWithoutPublishingAnEarlyResult() {
        ProductionTask task = accept();
        assertThat(conclusion(task)).isEqualTo(LEGAL.get(0));
        task.startChecking();
        assertThat(conclusion(task)).isEqualTo(LEGAL.get(1));
        task.completeChecking();
        assertThat(conclusion(task)).isEqualTo(LEGAL.get(2));
        task.completeGeneration();
        assertThat(conclusion(task)).isEqualTo(LEGAL.get(3));
        task.succeed(RESULT);
        assertThat(conclusion(task)).isEqualTo(LEGAL.get(4));
        assertFixedInput(task);
    }

    @ParameterizedTest(name = "failure at {0}")
    @MethodSource("stageFailures")
    void recordsEachActualFailureStageAndReason(TaskStage stage) {
        ProductionTask task = accept();
        task.startChecking();
        if (stage != TaskStage.CHECK_ASSETS) {
            task.completeChecking();
        }
        if (stage == TaskStage.FINALIZE) {
            task.completeGeneration();
        }
        task.failCurrentStage(stage, CODE, MESSAGE);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getCurrentStage()).isEqualTo(stage);
        assertThat(task.getFailure()).isEqualTo(stageFailure(stage));
        assertThat(task.getResult()).isNull();
        assertFixedInput(task);
    }

    @Test
    void dispatchFailureHasNoInventedExecutionStage() {
        ProductionTask task = accept();
        task.failBeforeStart(DISPATCH_FAILURE.code(), DISPATCH_FAILURE.message());
        assertThat(conclusion(task)).isEqualTo(LEGAL.get(8));
        assertFixedInput(task);
    }

    @ParameterizedTest(name = "state {0}, action {1}")
    @MethodSource("allOperations")
    void allowsOnlyTheEightDocumentedEdgesAndRejectsAllOtherOperations(int source, Action action) {
        ProductionTask task = restore(LEGAL.get(source));
        Edge edge = EDGES.stream().filter(e -> e.source() == source && e.action() == action)
                .findFirst().orElse(null);
        Conclusion before = conclusion(task);
        if (edge == null) {
            assertThatIllegalStateException().isThrownBy(() -> action.apply(task));
            assertThat(conclusion(task)).isEqualTo(before);
        } else {
            action.apply(task);
            assertThat(conclusion(task)).isEqualTo(LEGAL.get(edge.target()));
        }
        assertFixedInput(task);
    }

    @ParameterizedTest
    @MethodSource("legalConclusions")
    void restoresAllNineCombinationsWithoutReplayingOrChangingFacts(Conclusion state) {
        ProductionTask restored = restore(state);
        assertThat(conclusion(restored)).isEqualTo(state);
        assertFixedInput(restored);
    }

    @ParameterizedTest
    @MethodSource("illegalConclusions")
    void rejectsEveryOtherCombinationOfStatusStageResultAndFailure(Conclusion state) {
        assertThatIllegalArgumentException().isThrownBy(() -> restore(state));
    }

    @Test
    void rejectsSuccessWithoutAResultAndPreservesTheRunningState() {
        ProductionTask task = restore(LEGAL.get(3));
        assertThatNullPointerException().isThrownBy(() -> task.succeed(null));
        assertThat(conclusion(task)).isEqualTo(LEGAL.get(3));
    }

    @Test
    void invalidFailureDetailsNeverLeaveAPartialConclusion() {
        ProductionTask running = restore(LEGAL.get(2));
        assertThatIllegalArgumentException().isThrownBy(() -> running.failCurrentStage(TaskStage.GENERATE, " ", MESSAGE));
        assertThatNullPointerException().isThrownBy(() -> running.failCurrentStage(TaskStage.GENERATE, CODE, null));
        assertThatNullPointerException().isThrownBy(() -> running.failCurrentStage(null, CODE, MESSAGE));
        assertThat(conclusion(running)).isEqualTo(LEGAL.get(2));

        ProductionTask pending = accept();
        assertThatIllegalArgumentException().isThrownBy(() -> pending.failBeforeStart("DISPATCH_REJECTED", " "));
        assertThatNullPointerException().isThrownBy(() -> pending.failBeforeStart(null, MESSAGE));
        assertThat(conclusion(pending)).isEqualTo(LEGAL.get(0));
    }

    @Test
    void acceptanceAndRestorationRequireIdentityInputTimeAndStatus() {
        assertThatNullPointerException().isThrownBy(() -> ProductionTask.accept(null, OWNER_ID, CREATED_AT, INPUT));
        assertThatNullPointerException().isThrownBy(() -> ProductionTask.accept(TASK_ID, null, CREATED_AT, INPUT));
        assertThatNullPointerException().isThrownBy(() -> ProductionTask.accept(TASK_ID, OWNER_ID, null, INPUT));
        assertThatNullPointerException().isThrownBy(() -> ProductionTask.accept(TASK_ID, OWNER_ID, CREATED_AT, null));
        assertThatNullPointerException().isThrownBy(() -> ProductionTask.restore(
                TASK_ID, OWNER_ID, CREATED_AT, INPUT, null, null, null, null));
    }

    @Test
    void changingOneTaskDoesNotChangeAnotherTask() {
        ProductionTask first = accept();
        UUID secondId = UUID.fromString("283def77-032c-4e18-9d95-34b435a0f2f2");
        ProductionTask second = ProductionTask.accept(secondId, TASK_ID, CREATED_AT, INPUT);
        first.startChecking();
        first.failCurrentStage(TaskStage.CHECK_ASSETS, CODE, MESSAGE);
        assertThat(conclusion(second)).isEqualTo(LEGAL.get(0));
        assertThat(second.getTaskId()).isEqualTo(secondId);
        assertThat(second.getOwnerId()).isEqualTo(TASK_ID);
    }

    private static Stream<TaskStage> stageFailures() {
        return Arrays.stream(TaskStage.values());
    }

    private static Stream<Arguments> allOperations() {
        return java.util.stream.IntStream.range(0, LEGAL.size()).boxed()
                .flatMap(source -> Arrays.stream(Action.values()).map(action -> Arguments.of(source, action)));
    }

    private static Stream<Conclusion> legalConclusions() {
        return LEGAL.stream();
    }

    private static Stream<Conclusion> illegalConclusions() {
        List<TaskStage> stages = Arrays.asList(null, TaskStage.CHECK_ASSETS, TaskStage.GENERATE, TaskStage.FINALIZE);
        List<TaskResult> results = Arrays.asList(null, RESULT);
        List<FailureInfo> failures = Arrays.asList(null, DISPATCH_FAILURE,
                stageFailure(TaskStage.CHECK_ASSETS), stageFailure(TaskStage.GENERATE), stageFailure(TaskStage.FINALIZE));
        return Arrays.stream(TaskStatus.values()).flatMap(status -> stages.stream().flatMap(stage ->
                results.stream().flatMap(result -> failures.stream()
                        .map(failure -> new Conclusion(status, stage, result, failure)))))
                .filter(state -> !LEGAL.contains(state));
    }

    private static ProductionTask accept() {
        return ProductionTask.accept(TASK_ID, OWNER_ID, CREATED_AT, INPUT);
    }

    private static ProductionTask restore(Conclusion state) {
        return ProductionTask.restore(TASK_ID, OWNER_ID, CREATED_AT, INPUT,
                state.status(), state.stage(), state.result(), state.failure());
    }

    private static FailureInfo stageFailure(TaskStage stage) {
        return new FailureInfo(FailureKind.STAGE, stage, CODE, MESSAGE);
    }

    private static Conclusion conclusion(ProductionTask task) {
        return new Conclusion(task.getStatus(), task.getCurrentStage(), task.getResult(), task.getFailure());
    }

    private static void assertFixedInput(ProductionTask task) {
        assertThat(task.getTaskId()).isEqualTo(TASK_ID);
        assertThat(task.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(task.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(task.getInput()).isEqualTo(INPUT);
        assertThat(task.getInput().assetIds()).containsExactly(TASK_ID, OWNER_ID, TASK_ID);
    }

    private record Conclusion(TaskStatus status, TaskStage stage, TaskResult result, FailureInfo failure) {
    }

    private record Edge(int source, Action action, int target) {
    }

    private enum Action {
        START, COMPLETE_CHECK, COMPLETE_GENERATION, SUCCEED,
        FAIL_CHECK, FAIL_GENERATION, FAIL_FINALIZE, FAIL_DISPATCH;

        void apply(ProductionTask task) {
            switch (this) {
                case START -> task.startChecking();
                case COMPLETE_CHECK -> task.completeChecking();
                case COMPLETE_GENERATION -> task.completeGeneration();
                case SUCCEED -> task.succeed(RESULT);
                case FAIL_CHECK -> task.failCurrentStage(TaskStage.CHECK_ASSETS, CODE, MESSAGE);
                case FAIL_GENERATION -> task.failCurrentStage(TaskStage.GENERATE, CODE, MESSAGE);
                case FAIL_FINALIZE -> task.failCurrentStage(TaskStage.FINALIZE, CODE, MESSAGE);
                case FAIL_DISPATCH -> task.failBeforeStart(DISPATCH_FAILURE.code(), DISPATCH_FAILURE.message());
            }
        }
    }
}
