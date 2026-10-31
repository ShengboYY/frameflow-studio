package com.frameflow.studio.domain.asset.model.valobj;

public class AssetException extends RuntimeException {
    public enum Reason { NOT_FOUND, TOO_LARGE, UNSUPPORTED_TYPE, INVALID_IMAGE, UNAVAILABLE }
    private final Reason reason;

    public AssetException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
