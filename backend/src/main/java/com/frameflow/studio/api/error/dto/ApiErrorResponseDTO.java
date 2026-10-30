package com.frameflow.studio.api.error.dto;

import java.util.List;

public record ApiErrorResponseDTO(ErrorDTO error, String requestId) {
    public record ErrorDTO(String code, String message, List<DetailDTO> details) {}
    public record DetailDTO(String field, String code, String message) {}
}
