package com.frameflow.studio.cases.task.port;

/** Application simulation wait, outside database transactions and independently controllable in tests. */
@FunctionalInterface
public interface IStageDelay {
    void pause(int milliseconds) throws InterruptedException;
}
