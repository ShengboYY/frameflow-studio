package com.frameflow.studio.domain.task.model.valobj;

import java.util.Objects;

/** A stable logical reference; resource readiness is checked outside the aggregate. */
public record TaskResult(String resultReference) {

    public TaskResult {
        Objects.requireNonNull(resultReference, "resultReference is required");
        if (resultReference.isBlank()) {
            throw new IllegalArgumentException("resultReference must not be blank");
        }
    }
}
