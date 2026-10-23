package com.firstory.domain.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * local 프로필 전용 고정 인증번호 (firstory.sms.local-fixed-code). 프론트가 가입할 때마다 서버 로그에서
 * 인증번호를 찾지 않게 한다. 비어 있으면 local도 무작위 번호를 쓴다.
 * 번호 생성만 바꾸고 해시 저장, 만료, 시도 횟수 규칙은 그대로다.
 * <p>
 * 다른 프로필에 이 값이 있으면 {@link Guard}가 기동을 막는다 (운영에 고정 번호가 새지 않게).
 */
@Component
@Profile(LocalFixedVerificationCode.LOCAL_PROFILE)
public class LocalFixedVerificationCode {

    static final String LOCAL_PROFILE = "local";
    static final String PROPERTY = "firstory.sms.local-fixed-code";
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

    /**
     * local이 아닌 프로필에 고정 인증번호가 설정돼 있으면 무시하지 않고 기동을 실패시킨다.
     */
    @Component
    @Profile("!" + LOCAL_PROFILE)
    static class Guard {

        Guard(@Value("${" + PROPERTY + ":}") String code) {
            if (!code.isBlank()) {
                throw new IllegalStateException(
                        PROPERTY + "는 local 프로필에서만 쓸 수 있어요. 이 프로필의 설정에서 지워 주세요.");
            }
        }
    }
}
