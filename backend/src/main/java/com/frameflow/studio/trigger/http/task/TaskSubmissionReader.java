package com.frameflow.studio.trigger.http.task;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.frameflow.studio.api.task.dto.TaskSubmissionRequestDTO;
import com.frameflow.studio.cases.task.model.ExecutionOptions;

@Component
public class TaskSubmissionReader {
    private static final int MAX_BYTES = 65536;
    private final ObjectMapper mapper;
    private final boolean controlsEnabled;

    public TaskSubmissionReader(ObjectMapper mapper, @Value("${app.tasks.mock-controls-enabled}") boolean controlsEnabled) {
        this.mapper = mapper.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        this.controlsEnabled = controlsEnabled;
    }

    public TaskSubmissionRequestDTO read(HttpServletRequest request) throws IOException {
        if (request.getQueryString() != null) throw invalid();
        if (request.getContentLengthLong() > MAX_BYTES) throw tooLarge();
        byte[] bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw tooLarge();
        try {
            var root = mapper.readTree(bytes);
            requireObject(root, Set.of("templateId", "assetIds", "simulation"));
            if (!root.path("templateId").isString()
                    || !root.get("templateId").asString().matches("[a-z0-9][a-z0-9-]{0,63}")
                    || !root.path("assetIds").isArray()) throw invalid();
            for (var id : root.get("assetIds")) {
                if (!id.isString() || !validUuid(id.asString())) throw invalid();
            }
            if (root.has("simulation")) {
                var simulation = root.get("simulation");
                requireObject(simulation, Set.of("failAtStage", "stageDelayMs"));
                if (!controlsEnabled) throw new InvalidRequest(400, "SIMULATION_CONTROLS_DISABLED", "模拟控制未启用");
                if (simulation.has("failAtStage") && !simulation.get("failAtStage").isNull()) {
                    var stage = simulation.get("failAtStage");
                    if (!stage.isString() || !Set.of("CHECK_ASSETS", "GENERATE", "FINALIZE").contains(stage.asString())) {
                        throw invalidSimulation();
                    }
                }
                if (simulation.has("stageDelayMs")) {
                    var delay = simulation.get("stageDelayMs");
                    if (!delay.isIntegralNumber() || !delay.canConvertToInt() || delay.asInt() < 0 || delay.asInt() > 30000) {
                        throw invalidSimulation();
                    }
                }
            }
            return mapper.treeToValue(root, TaskSubmissionRequestDTO.class);
        } catch (JacksonException failure) {
            throw invalid();
        }
    }

    public ExecutionOptions options(TaskSubmissionRequestDTO dto) {
        var simulation = dto.simulation();
        return simulation == null ? ExecutionOptions.from(null, 1000) : ExecutionOptions.from(
                simulation.failAtStage(),
                simulation.stageDelayMs() == null ? 1000 : simulation.stageDelayMs());
    }

    public static boolean validUuid(String value) {
        return value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private void requireObject(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject()) throw invalid();
        for (String name : node.propertyNames()) if (!fields.contains(name)) throw invalid();
    }

    private InvalidRequest invalid() { return new InvalidRequest(400, "INVALID_REQUEST", "请求参数不合法"); }
    private InvalidRequest invalidSimulation() { return new InvalidRequest(400, "INVALID_SIMULATION", "模拟设置不合法"); }
    private InvalidRequest tooLarge() { return new InvalidRequest(413, "PAYLOAD_TOO_LARGE", "请求超过大小限制"); }

    public static class InvalidRequest extends RuntimeException {
        private final int status;
        private final String code;
        public InvalidRequest(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
        public int status() { return status; }
        public String code() { return code; }
    }
}
