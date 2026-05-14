package com.haru.common.exception;

public class HaruException extends RuntimeException {

    private final ErrorCode errorCode;

    public HaruException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
