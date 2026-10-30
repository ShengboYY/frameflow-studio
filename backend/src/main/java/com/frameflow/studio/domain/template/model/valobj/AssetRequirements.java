package com.frameflow.studio.domain.template.model.valobj;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record AssetRequirements(int requiredImageCount, List<String> acceptedMimeTypes, long maxFileSizeBytes) {
    public AssetRequirements {
        acceptedMimeTypes = List.copyOf(acceptedMimeTypes);
        if (requiredImageCount < 1 || maxFileSizeBytes < 1 || maxFileSizeBytes > 8L * 1024 * 1024
                || acceptedMimeTypes.isEmpty()
                || Set.copyOf(acceptedMimeTypes).size() != acceptedMimeTypes.size()
                || !Set.of("image/jpeg", "image/png").containsAll(acceptedMimeTypes)) {
            throw new IllegalArgumentException("Invalid template asset requirements");
        }
    }

    public List<Violation> validateImage(ImageDescriptor image) {
        var violations = new ArrayList<Violation>();
        if (image.mediaType() == null || !acceptedMimeTypes.contains(image.mediaType())) violations.add(Violation.IMAGE_TYPE);
        if (image.sizeBytes() <= 0 || image.sizeBytes() > maxFileSizeBytes) violations.add(Violation.IMAGE_SIZE);
        return List.copyOf(violations);
    }

    public List<Violation> validateSubmission(List<ImageDescriptor> images) {
        var violations = new ArrayList<Violation>();
        if (images.size() != requiredImageCount) violations.add(Violation.IMAGE_COUNT);
        // Count positions, including repeated references; uploading one image does not validate the total.
        for (ImageDescriptor image : images) violations.addAll(validateImage(image));
        return List.copyOf(violations);
    }

    public record ImageDescriptor(String mediaType, long sizeBytes) {}
    public enum Violation { IMAGE_COUNT, IMAGE_TYPE, IMAGE_SIZE }
}
