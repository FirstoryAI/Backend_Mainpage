package com.firstory.domain.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
@Profile({LocalFixedVerificationCode.LOCAL_PROFILE, LocalFixedVerificationCode.DEV_PROFILE})
public class LocalFixedVerificationCode {

    static final String LOCAL_PROFILE = "local";
    static final String DEV_PROFILE = "dev";
    static final String PROPERTY = "firstory.auth.local-fixed-code";
    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");

    private final String code;

    public LocalFixedVerificationCode(@Value("${" + PROPERTY + ":}") String code) {
        if (!code.isBlank() && !SIX_DIGITS.matcher(code).matches()) {
            throw new IllegalStateException(PROPERTY + "는 숫자 6자리여야 해요");
        }
        this.code = code.isBlank() ? null : code;
    }

    public Optional<String> code() {
        return Optional.ofNullable(code);
    }

    @Component
    @Profile("!" + LOCAL_PROFILE + " & !" + DEV_PROFILE)
    static class Guard {

        Guard(@Value("${" + PROPERTY + ":}") String code) {
            if (!code.isBlank()) {
                throw new IllegalStateException(
                        PROPERTY + "는 local, dev 프로필에서만 쓸 수 있어요. 이 프로필의 설정에서 지워 주세요.");
            }
        }
    }
}
