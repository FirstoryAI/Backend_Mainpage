package com.firstory.domain.auth.entity;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;

import java.util.regex.Pattern;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 20;
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL =
            Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{}:;,.?/\\\\]");
    private static final Pattern INVALID_CHAR =
            Pattern.compile("[^A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{}:;,.?/\\\\]");

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null
                || rawPassword.length() < MIN_LENGTH
                || rawPassword.length() > MAX_LENGTH
                || !LETTER.matcher(rawPassword).find()
                || !DIGIT.matcher(rawPassword).find()
                || !SPECIAL.matcher(rawPassword).find()
                || INVALID_CHAR.matcher(rawPassword).find()) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }
}