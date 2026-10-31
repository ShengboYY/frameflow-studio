package com.frameflow.studio.trigger.http.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.frameflow.studio.api.error.dto.ApiErrorResponseDTO;
import com.frameflow.studio.api.task.dto.TaskSubmissionErrorResponseDTO;
import com.frameflow.studio.cases.asset.model.AssetOperationException;
import com.frameflow.studio.cases.task.model.TaskOperationException;
import com.frameflow.studio.trigger.http.task.TaskSubmissionReader;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final JsonErrors errors;

    public ApiExceptionHandler(JsonErrors errors) {
        this.errors = errors;
    }

    @ExceptionHandler(TaskSubmissionReader.InvalidRequest.class)
    ResponseEntity<ApiErrorResponseDTO> taskRequest(TaskSubmissionReader.InvalidRequest exception, HttpServletRequest request) {
        return error(request, exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(TaskOperationException.class)
    ResponseEntity<?> task(TaskOperationException exception, HttpServletRequest request) {
        return switch (exception.reason()) {
            case NOT_FOUND -> error(request, 404, "RESOURCE_NOT_FOUND", "资源不存在");
            case RESULT_NOT_READY -> error(request, 409, "RESULT_NOT_READY", "任务尚未成功，结果不可用");
            case SUBMISSION_OUTCOME_UNKNOWN -> ResponseEntity.status(503).contentType(MediaType.APPLICATION_JSON)
                    .body(new TaskSubmissionErrorResponseDTO(new TaskSubmissionErrorResponseDTO.ErrorDTO("SUBMISSION_OUTCOME_UNKNOWN",
                            "提交结果未知，请先查询个人记录，勿自动重试", java.util.List.of(), exception.taskId()),
                            (String) request.getAttribute(JsonErrors.REQUEST_ID)));
            case REQUIREMENTS_MISMATCH -> ResponseEntity.unprocessableContent().contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponseDTO(new ApiErrorResponseDTO.ErrorDTO("TEMPLATE_REQUIREMENTS_MISMATCH",
                            "素材不符合当前模板要求", exception.violations().stream().map(violation ->
                                    new ApiErrorResponseDTO.DetailDTO("assetIds", switch (violation) {
                                        case IMAGE_COUNT -> "COUNT_MISMATCH";
                                        case IMAGE_TYPE -> "TYPE_MISMATCH";
                                        case IMAGE_SIZE -> "SIZE_MISMATCH";
                                    }, "素材数量、类型或大小不符合模板要求")).toList()),
                            (String) request.getAttribute(JsonErrors.REQUEST_ID)));
        };
    }

    @ExceptionHandler(AssetOperationException.class)
    ResponseEntity<ApiErrorResponseDTO> asset(AssetOperationException exception, HttpServletRequest request) {
        return switch (exception.reason()) {
            case NOT_FOUND -> error(request, 404, "RESOURCE_NOT_FOUND", "资源不存在");
            case TOO_LARGE -> error(request, 413, "PAYLOAD_TOO_LARGE", "图片或请求超过大小限制");
            case UNSUPPORTED_TYPE -> error(request, 415, "UNSUPPORTED_MEDIA_TYPE", "图片类型不支持");
            case INVALID_IMAGE -> error(request, 422, "INVALID_IMAGE", "图片无法读取");
            case UNAVAILABLE -> error(request, 503, "RESOURCE_UNAVAILABLE", "素材暂时不可用");
        };
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponseDTO> tooLarge(HttpServletRequest request) {
        return error(request, 413, "PAYLOAD_TOO_LARGE", "图片或请求超过大小限制");
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiErrorResponseDTO> multipart(HttpServletRequest request) {
        return error(request, 400, "INVALID_REQUEST", "上传请求不合法");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
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
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(errors.body(request, code, message));
    }
}
