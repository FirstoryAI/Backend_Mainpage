package com.firstory.global.error;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 메시지의 {n}을 채운다 (예: CUSTOM_VALUE_TOO_LONG의 항목별 글자 수).
     */
    public BusinessException(ErrorCode errorCode, int n) {
        super(errorCode.getMessage().replace("{n}", String.valueOf(n)));
        this.errorCode = errorCode;
    }
}
