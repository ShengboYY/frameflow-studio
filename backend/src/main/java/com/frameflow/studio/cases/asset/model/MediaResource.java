package com.frameflow.studio.cases.asset.model;

import com.frameflow.studio.domain.asset.model.valobj.PackagedImage;

public record MediaResource(String reference, String mediaType, byte[] bytes) {
    public MediaResource {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public static MediaResource from(PackagedImage image) {
        return new MediaResource(image.reference(), image.mediaType(), image.bytes());
    }
}
