package com.frameflow.studio.cases.task.model;

import java.util.List;
import java.util.UUID;
import com.frameflow.studio.cases.template.model.TemplateValidation.Violation;

public class TaskOperationException extends RuntimeException {
    public enum Reason { NOT_FOUND, REQUIREMENTS_MISMATCH, RESULT_NOT_READY, SUBMISSION_OUTCOME_UNKNOWN }
    private final Reason reason;
    private final UUID taskId;
    private final List<Violation> violations;

    public TaskOperationException(Reason reason) { this(reason, null, List.of()); }

    public TaskOperationException(Reason reason, UUID taskId, List<Violation> violations) {
        super(reason.name());
        this.reason = reason;
        this.taskId = taskId;
        this.violations = List.copyOf(violations);
    }

    public Reason reason() { return reason; }
    public UUID taskId() { return taskId; }
    public List<Violation> violations() { return violations; }
}
