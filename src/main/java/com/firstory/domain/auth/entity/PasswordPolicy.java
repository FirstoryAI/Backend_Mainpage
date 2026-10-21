package com.firstory.domain.auth.entity;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;

import java.util.regex.Pattern;

// TODO(PM): 비밀번호 규칙 확정. 지금은 8~64자, 영문과 숫자 각각 1개 이상
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null
                || rawPassword.length() < MIN_LENGTH
                || rawPassword.length() > MAX_LENGTH
                || !LETTER.matcher(rawPassword).find()
                || !DIGIT.matcher(rawPassword).find()) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }
}
