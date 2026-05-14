package com.haru.common.exception;

public class ForbiddenException extends HaruException {

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
