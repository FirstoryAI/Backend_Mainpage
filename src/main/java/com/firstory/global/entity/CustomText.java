package com.firstory.global.entity;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;

/**
 * "기타"를 골랐을 때 받는 직접 입력.
 * 기타를 골랐으면 필수, 고르지 않았으면 보내와도 버린다. 길이는 앞뒤 공백을 지우고 글자(code point) 수로 센다.
 */
public final class CustomText {

    private CustomText() {
    }

    /**
     * @return 앞뒤 공백을 지운 값. 기타를 고르지 않았으면 null
     */
    public static String resolve(boolean otherSelected, String raw, int maxLength) {
        if (!otherSelected) {
            return null;
        }
        String stripped = strip(raw);
        if (stripped == null) {
            throw new BusinessException(ErrorCode.CUSTOM_VALUE_REQUIRED);
        }
        return checkLength(stripped, maxLength);
    }

    /**
     * @return 앞뒤 공백을 지운 값. 비었으면 null
     */
    public static String strip(String raw) {
        if (raw == null) {
            return null;
        }
        String stripped = raw.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    public static String checkLength(String value, int maxLength) {
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new BusinessException(ErrorCode.CUSTOM_VALUE_TOO_LONG, maxLength);
        }
        return value;
    }
}
