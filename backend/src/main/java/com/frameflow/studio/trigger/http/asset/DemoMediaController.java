package com.frameflow.studio.trigger.http.asset;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.frameflow.studio.cases.asset.IMediaResourceCase;
import com.frameflow.studio.trigger.http.error.JsonErrors;

@RestController
@RequestMapping("/api/v1/demo-media")
public class DemoMediaController {
    private final IMediaResourceCase media;
    private final JsonErrors errors;

    public DemoMediaController(IMediaResourceCase media, JsonErrors errors) {
        this.media = media;
        this.errors = errors;
    }

    @GetMapping("/{mediaKey}")
    public ResponseEntity<?> cover(@PathVariable String mediaKey, HttpServletRequest request) {
        if (!mediaKey.matches("[a-z0-9][a-z0-9-]{0,63}") || !request.getParameterMap().isEmpty()) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body(errors.body(request, "INVALID_REQUEST", "请求参数不合法"));
        }
        var image = media.findCover(mediaKey);
        if (image.isEmpty()) {
            return ResponseEntity.status(404).contentType(MediaType.APPLICATION_JSON)
                    .body(errors.body(request, "RESOURCE_NOT_FOUND", "资源不存在"));
        }
        var resource = image.orElseThrow();
        byte[] bytes = resource.bytes();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(resource.mediaType()))
                .contentLength(bytes.length).body(bytes);
    }
}
