package com.frameflow.studio.domain.task.adapter.port;

import java.util.Objects;
import com.frameflow.studio.domain.task.model.valobj.TaskInput;

public interface IMediaGeneratorPort {
    Candidate generate(TaskInput input);

    /** A candidate is not a ready TaskResult; the execution Case must still finalize it. */
    record Candidate(String reference) {
        public Candidate {
            Objects.requireNonNull(reference, "Candidate reference is required");
            if (reference.isBlank()) throw new IllegalArgumentException("Candidate reference must not be blank");
        }
    }
}
