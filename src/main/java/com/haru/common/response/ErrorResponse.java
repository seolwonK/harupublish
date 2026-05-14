package com.haru.common.response;

public record ErrorResponse(
        boolean success,
        ErrorBody error
) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, new ErrorBody(code, message));
    }

    public record ErrorBody(
            String code,
            String message
    ) {
    }
}
