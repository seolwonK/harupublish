package com.haru.common.exception;

public class BusinessException extends HaruException {

    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
