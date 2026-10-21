package com.firstory.global.crypto;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;

import java.util.regex.Pattern;

/**
 * 휴대폰 번호 값 객체. 숫자 11자리(010xxxxxxxx)로 보관한다.
 * 로그에 번호가 남지 않도록 toString은 번호를 가린다.
 */
public record PhoneNumber(String digits) {

    private static final Pattern DIGITS = Pattern.compile("^010\\d{8}$");
    private static final Pattern DISPLAY = Pattern.compile("^010-\\d{4}-\\d{4}$");

    public PhoneNumber {
        if (digits == null || !DIGITS.matcher(digits).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
        }
    }

    public static PhoneNumber fromDisplay(String display) {
        String trimmed = display == null ? "" : display.trim();
        if (!DISPLAY.matcher(trimmed).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
        }
        return new PhoneNumber(trimmed.replace("-", ""));
    }

    public String display() {
        return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
    }

    /**
     * 가운데 4자리를 가린 형태(010-****-5678). 응답과 로그에는 이 형태만 쓴다.
     */
    public String masked() {
        return digits.substring(0, 3) + "-****-" + digits.substring(7);
    }

    @Override
    public String toString() {
        return "PhoneNumber[***]";
    }
}
