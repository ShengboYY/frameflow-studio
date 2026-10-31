package com.frameflow.studio.infrastructure.adapter.port.task;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.frameflow.studio.cases.task.model.ExecutionOptions;
import com.frameflow.studio.domain.task.model.valobj.TaskStage;

import static org.assertj.core.api.Assertions.*;

class ExecutorTaskDispatcherPortTest {
    @Test
    void boundedQueueRejectsWithoutCallerRunsAndRetainsEachEnvelopeAfterCallerReturns() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(2);
        var calls = new CopyOnWriteArrayList<UUID>();
        var captured = new CopyOnWriteArrayList<ExecutionOptions>();
        var thread = new AtomicReference<Thread>();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var firstOptions = new ExecutionOptions(TaskStage.GENERATE, 5);
        var secondOptions = new ExecutionOptions(null, 19);
        try (var dispatcher = new ExecutorTaskDispatcherPort(1, 1, (id, options) -> {
            thread.set(Thread.currentThread());
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timed out");
                calls.add(id);
                captured.add(options);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally { finished.countDown(); }
        })) {
            try {
                assertThat(dispatcher.dispatch(first, firstOptions)).isTrue();
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(dispatcher.dispatch(second, secondOptions)).isTrue();
                assertThat(dispatcher.dispatch(UUID.randomUUID(), new ExecutionOptions(null, 0))).isFalse();
                assertThat(thread.get()).isNotSameAs(Thread.currentThread());
                assertThat(calls).isEmpty();
            } finally { release.countDown(); }
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).containsExactly(first, second);
            assertThat(captured).containsExactly(firstOptions, secondOptions);
        }
    }

    @Test
    void shutdownDrainsAcceptedWorkAndRejectsNewDispatch() {
        var calls = new CopyOnWriteArrayList<UUID>();
        var dispatcher = new ExecutorTaskDispatcherPort(1, 2, (id, options) -> calls.add(id));
        UUID id = UUID.randomUUID();
        assertThat(dispatcher.dispatch(id, new ExecutionOptions(null, 0))).isTrue();
        dispatcher.close();
        assertThat(calls).containsExactly(id);
        assertThat(dispatcher.dispatch(UUID.randomUUID(), new ExecutionOptions(null, 0))).isFalse();
    }

    @Test
    void interruptedShutdownInterruptsRunningWorkAndPreservesInterruptFlag() throws Exception {
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var dispatcher = new ExecutorTaskDispatcherPort(1, 1, (id, options) -> {
            started.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException stop) { interrupted.countDown(); Thread.currentThread().interrupt(); }
        });
        try {
            dispatcher.dispatch(UUID.randomUUID(), new ExecutionOptions(null, 0));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.currentThread().interrupt();
            dispatcher.close();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); dispatcher.close(); }
        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void rejectsInvalidBoundsAndDelayValuesAndMockProducesOnlyCandidate() {
        assertThatThrownBy(() -> new ExecutorTaskDispatcherPort(0, 1, (id, options) -> {})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutorTaskDispatcherPort(1, 0, (id, options) -> {})).isInstanceOf(IllegalArgumentException.class);
        for (int delay : List.of(-1, 30001)) {
            assertThatThrownBy(() -> new ExecutionOptions(null, delay)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new ExecutionOptions(null, 0).stageDelayMs()).isZero();
        assertThat(new ExecutionOptions(null, 30000).stageDelayMs()).isEqualTo(30000);
        var input = new com.frameflow.studio.domain.task.model.valobj.TaskInput("portrait-card", List.of(UUID.randomUUID()));
        assertThat(new MockMediaGeneratorPort().generate(input).reference()).isEqualTo("demo:portrait-result");
    }
}
