package com.frameflow.studio.trigger.http.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.frameflow.studio.api.error.dto.ApiErrorResponseDTO;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final JsonErrors errors;

    public ApiExceptionHandler(JsonErrors errors) {
        this.errors = errors;
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiErrorResponseDTO> unavailable(HttpServletRequest request) {
        return error(request, 503, "SERVICE_UNAVAILABLE", "服务暂时不可用");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponseDTO> notFound(HttpServletRequest request) {
        return error(request, 404, "RESOURCE_NOT_FOUND", "资源不存在");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponseDTO> method(HttpServletRequest request) {
        return error(request, 405, "METHOD_NOT_ALLOWED", "请求方法不支持");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponseDTO> media(HttpServletRequest request) {
        return error(request, 415, "UNSUPPORTED_MEDIA_TYPE", "请求编码不支持");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponseDTO> unexpected(HttpServletRequest request) {
        return error(request, 500, "INTERNAL_ERROR", "服务处理失败");
    }

    private ResponseEntity<ApiErrorResponseDTO> error(HttpServletRequest request, int status,
                                                     String code, String message) {
        return ResponseEntity.status(status).body(errors.body(request, code, message));
    }
}
