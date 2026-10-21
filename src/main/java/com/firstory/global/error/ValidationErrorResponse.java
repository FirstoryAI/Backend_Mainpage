package com.firstory.global.error;

import java.util.List;

public record ValidationErrorResponse(String code, String message, List<FieldMessage> fields) {

    public record FieldMessage(String field, String message) {
    }

    public static ValidationErrorResponse of(List<FieldMessage> fields) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        return new ValidationErrorResponse(errorCode.name(), errorCode.getMessage(), fields);
    }
}
