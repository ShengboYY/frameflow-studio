package com.frameflow.studio.api.task.dto;

import java.util.List;
import java.util.UUID;
import com.frameflow.studio.api.error.dto.ApiErrorResponseDTO.DetailDTO;

/** The uncertain-submission variant adds a traceable ID without changing other error responses. */
public record TaskSubmissionErrorResponseDTO(ErrorDTO error, String requestId) {
    public record ErrorDTO(String code, String message, List<DetailDTO> details, UUID taskId) { }
}
