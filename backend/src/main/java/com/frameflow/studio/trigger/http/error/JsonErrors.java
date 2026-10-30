package com.frameflow.studio.trigger.http.error;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.frameflow.studio.api.error.dto.ApiErrorResponseDTO;

@Component
public class JsonErrors {
    public static final String REQUEST_ID = JsonErrors.class.getName() + ".requestId";
    private final ObjectMapper mapper;

    public JsonErrors(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ApiErrorResponseDTO body(HttpServletRequest request, String code, String message) {
        return new ApiErrorResponseDTO(new ApiErrorResponseDTO.ErrorDTO(code, message, List.of()),
                (String) request.getAttribute(REQUEST_ID));
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), body(request, code, message));
    }
}
