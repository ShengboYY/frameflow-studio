package com.frameflow.studio.trigger.http.task;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.frameflow.studio.api.task.dto.TaskAcceptedResponseDTO;
import com.frameflow.studio.api.task.dto.TaskListResponseDTO;
import com.frameflow.studio.api.task.dto.TaskResponseDTO;
import com.frameflow.studio.cases.identity.model.AccountView;
import com.frameflow.studio.cases.task.ICreateProductionTaskCase;
import com.frameflow.studio.cases.task.IQueryProductionTaskCase;
import com.frameflow.studio.cases.task.IReadTaskResultCase;
import com.frameflow.studio.cases.task.model.TaskView;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final ICreateProductionTaskCase create;
    private final IQueryProductionTaskCase query;
    private final IReadTaskResultCase result;
    private final TaskSubmissionReader submissions;

    public TaskController(ICreateProductionTaskCase create, IQueryProductionTaskCase query,
                          IReadTaskResultCase result, TaskSubmissionReader submissions) {
        this.create = create;
        this.query = query;
        this.result = result;
        this.submissions = submissions;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TaskAcceptedResponseDTO> create(Authentication authentication, HttpServletRequest request) throws IOException {
        var dto = submissions.read(request);
        var accepted = create.create(owner(authentication), dto.templateId(), dto.assetIds().stream().map(UUID::fromString).toList(),
                submissions.options(dto));
        return ResponseEntity.accepted().location(URI.create("/api/v1/tasks/" + accepted.taskId()))
                .body(new TaskAcceptedResponseDTO(accepted.taskId(), accepted.ownerId(), accepted.createdAt()));
    }

    @GetMapping
    public TaskListResponseDTO list(Authentication authentication, HttpServletRequest request) {
        validateQuery(request);
        return new TaskListResponseDTO(query.list(owner(authentication)).stream().map(this::response).toList());
    }

    @GetMapping("/{taskId}")
    public TaskResponseDTO find(@PathVariable String taskId, Authentication authentication, HttpServletRequest request) {
        validateQuery(request);
        return response(query.find(owner(authentication), id(taskId)));
    }

    @GetMapping("/{taskId}/result")
    public ResponseEntity<byte[]> result(@PathVariable String taskId, Authentication authentication, HttpServletRequest request) {
        validateQuery(request);
        var resource = result.read(owner(authentication), id(taskId));
        byte[] bytes = resource.bytes();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(resource.mediaType())).contentLength(bytes.length).body(bytes);
    }

    private TaskResponseDTO response(TaskView view) {
        var failure = view.failure();
        return new TaskResponseDTO(view.taskId(), view.ownerId(), view.templateId(), view.status(),
                view.currentStage(), view.revision(), view.createdAt(), view.updatedAt(),
                view.result() == null ? null : new TaskResponseDTO.ResultDTO("/api/v1/tasks/" + view.taskId() + "/result", view.result().mediaType()),
                failure == null ? null : new TaskResponseDTO.FailureDTO(failure.kind(), failure.stage(), failure.code(), failure.message()));
    }

    private UUID owner(Authentication authentication) { return ((AccountView) authentication.getPrincipal()).ownerId(); }
    private UUID id(String value) {
        if (!TaskSubmissionReader.validUuid(value)) throw new TaskSubmissionReader.InvalidRequest(400, "INVALID_REQUEST", "任务标识不合法");
        return UUID.fromString(value);
    }
    private void validateQuery(HttpServletRequest request) {
        if (request.getQueryString() != null) throw new TaskSubmissionReader.InvalidRequest(400, "INVALID_REQUEST", "请求参数不合法");
    }
}
