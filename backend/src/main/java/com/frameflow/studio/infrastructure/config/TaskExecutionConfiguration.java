package com.frameflow.studio.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.frameflow.studio.cases.task.port.IStageDelay;
import com.frameflow.studio.cases.task.port.ITaskDispatcher;
import com.frameflow.studio.domain.task.adapter.port.IMediaGeneratorPort;
import com.frameflow.studio.infrastructure.adapter.port.task.ExecutorTaskDispatcherPort;
import com.frameflow.studio.infrastructure.adapter.port.task.MockMediaGeneratorPort;

@Configuration(proxyBeanMethods = false)
public class TaskExecutionConfiguration {
    @Bean
    IMediaGeneratorPort mediaGenerator() {
        return new MockMediaGeneratorPort();
    }

    @Bean
    IStageDelay stageDelay() {
        return Thread::sleep;
    }

    @Bean(destroyMethod = "close")
    ExecutorTaskDispatcherPort taskDispatcher(@Value("${app.tasks.worker-count}") int workers,
                                             @Value("${app.tasks.queue-capacity}") int capacity,
                                             ITaskDispatcher.ExecutionCallback callback) {
        return new ExecutorTaskDispatcherPort(workers, capacity, callback);
    }
}
