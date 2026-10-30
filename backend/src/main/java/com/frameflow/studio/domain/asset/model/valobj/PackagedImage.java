package com.frameflow.studio.domain.asset.model.valobj;

public record PackagedImage(String reference, String mediaType, byte[] bytes) {
    public PackagedImage {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
