package com.frameflow.studio.domain.task.model.valobj;

import java.util.Objects;

/** Code and message are sanitized business facts, never raw exception details. */
public record FailureInfo(FailureKind kind, TaskStage stage, String code, String message) {

    public FailureInfo {
        Objects.requireNonNull(kind, "failure kind is required");
        Objects.requireNonNull(code, "failure code is required");
        Objects.requireNonNull(message, "failure message is required");
        if (code.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("Failure code and message must not be blank");
        }
        if ((kind == FailureKind.STAGE && stage == null)
                || (kind == FailureKind.DISPATCH && stage != null)) {
            throw new IllegalArgumentException("Failure kind and stage must agree");
        }
    }
}
