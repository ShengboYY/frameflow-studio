package com.frameflow.studio.infrastructure.adapter.port.task;

import com.frameflow.studio.domain.task.adapter.port.IMediaGeneratorPort;
import com.frameflow.studio.domain.task.model.valobj.TaskInput;

/** Deliberately returns a fixture candidate, not an image generated from the uploaded content. */
public class MockMediaGeneratorPort implements IMediaGeneratorPort {
    @Override
    public Candidate generate(TaskInput input) {
        return new Candidate(switch (input.templateId()) {
            case "portrait-card" -> "demo:portrait-result";
            case "travel-postcard" -> "demo:postcard-result";
            default -> throw new IllegalArgumentException("No mock result for template");
        });
    }
}
