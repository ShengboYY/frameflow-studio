package com.frameflow.studio.trigger.http.asset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.frameflow.studio.api.asset.dto.AssetResponseDTO;
import com.frameflow.studio.cases.asset.IMediaResourceCase;
import com.frameflow.studio.cases.asset.IUploadAssetCase;
import com.frameflow.studio.cases.asset.model.AssetOperationException;
import com.frameflow.studio.cases.identity.model.AccountView;
import com.frameflow.studio.trigger.http.error.JsonErrors;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {
    private final IUploadAssetCase upload;
    private final IMediaResourceCase media;
    private final JsonErrors errors;

    public AssetController(IUploadAssetCase upload, IMediaResourceCase media, JsonErrors errors) {
        this.upload = upload;
        this.media = media;
        this.errors = errors;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(Authentication authentication, HttpServletRequest request)
            throws IOException, ServletException {
        var parts = request.getParts();
        var file = request.getPart("file");
        var template = request.getPart("templateId");
        if (request.getQueryString() != null || parts.size() != 2 || file == null || template == null
                || file.getSubmittedFileName() == null || template.getSubmittedFileName() != null || template.getSize() > 64) return invalid(request);
        String templateId;
        try (var input = template.getInputStream()) {
            templateId = new String(input.readNBytes(65), StandardCharsets.UTF_8);
        }
        if (!templateId.matches("[a-z0-9][a-z0-9-]{0,63}")) return invalid(request);
        AccountView owner = (AccountView) authentication.getPrincipal();
        try (var input = file.getInputStream()) {
            var asset = upload.upload(owner.ownerId(), templateId, input);
            return ResponseEntity.status(201).body(new AssetResponseDTO(asset.assetId(), asset.ownerId(),
                    asset.mediaType(), asset.sizeBytes(), asset.createdAt(),
                    "/api/v1/assets/" + asset.assetId() + "/content"));
        } catch (IOException exception) {
            throw new AssetOperationException(AssetOperationException.Reason.UNAVAILABLE);
        }
    }

    @GetMapping("/{assetId}/content")
    public ResponseEntity<?> content(@PathVariable String assetId, Authentication authentication,
                                     HttpServletRequest request) {
        if (!assetId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || !request.getParameterMap().isEmpty()) return invalid(request);
        AccountView owner = (AccountView) authentication.getPrincipal();
        var resource = media.input(owner.ownerId(), UUID.fromString(assetId));
        byte[] bytes = resource.bytes();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(resource.mediaType()))
                .contentLength(bytes.length).body(bytes);
    }

    private ResponseEntity<?> invalid(HttpServletRequest request) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(errors.body(request, "INVALID_REQUEST", "请求参数不合法"));
    }
}
