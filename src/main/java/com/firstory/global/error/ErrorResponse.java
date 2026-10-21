package com.firstory.global.error;

public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }

    public static ErrorResponse of(BusinessException e) {
        return new ErrorResponse(e.getErrorCode().name(), e.getMessage());
    }
}
