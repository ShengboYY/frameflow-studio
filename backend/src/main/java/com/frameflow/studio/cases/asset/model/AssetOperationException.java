package com.frameflow.studio.cases.asset.model;

public class AssetOperationException extends RuntimeException {
    public enum Reason { NOT_FOUND, TOO_LARGE, UNSUPPORTED_TYPE, INVALID_IMAGE, UNAVAILABLE }
    private final Reason reason;

    public AssetOperationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public static AssetOperationException from(com.frameflow.studio.domain.asset.model.valobj.AssetException failure) {
        return new AssetOperationException(Reason.valueOf(failure.reason().name()));
    }

    public Reason reason() { return reason; }
}
