package com.frameflow.studio.infrastructure.adapter.port.task;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.frameflow.studio.cases.task.model.ExecutionOptions;
import com.frameflow.studio.cases.task.port.ITaskDispatcher;

public class ExecutorTaskDispatcherPort implements ITaskDispatcher, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ExecutorTaskDispatcherPort.class);
    private final ThreadPoolExecutor executor;
    private final ExecutionCallback callback;

    public ExecutorTaskDispatcherPort(int workers, int capacity, ExecutionCallback callback) {
        if (workers < 1 || capacity < 1) throw new IllegalArgumentException("Worker and queue sizes must be positive");
        this.callback = Objects.requireNonNull(callback);
        var sequence = new AtomicInteger();
        executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), work -> {
                    var thread = new Thread(work, "production-task-" + sequence.incrementAndGet());
                    // Hard shutdown must not leave uncooperative work keeping the process alive.
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public boolean dispatch(UUID taskId, ExecutionOptions options) {
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(options);
        try {
            executor.execute(() -> callback.execute(taskId, options));
            return true;
        } catch (RejectedExecutionException rejected) {
            return false;
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) interruptRemaining();
        } catch (InterruptedException interrupted) {
            interruptRemaining();
            Thread.currentThread().interrupt();
        }
    }

    private void interruptRemaining() {
        int queued = executor.shutdownNow().size();
        log.warn("Task executor interrupted remaining work; {} queued dispatches will not run", queued);
    }
}
