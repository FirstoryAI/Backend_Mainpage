package com.firstory.domain.auth.entity;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 세션에 넣는 가입 전 카카오 회원. 카카오 로그인에서 처음 본 회원이면 남기고, 카카오 가입에서 한 번 쓰고 지운다.
 *
 * @param email         카카오가 준 이메일(소문자). 없으면 null
 * @param emailVerified 카카오가 인증한 이메일이면 우리 이메일 인증을 건너뛴다
 */
public record PendingKakaoSignup(String kakaoId, String email, boolean emailVerified, Instant createdAt)
        implements Serializable {

    public static final Duration USABLE_FOR = Duration.ofMinutes(10);

    public PendingKakaoSignup {
        Objects.requireNonNull(kakaoId);
        Objects.requireNonNull(createdAt);
    }

    public boolean isUsable(Instant now) {
        return now.isBefore(createdAt.plus(USABLE_FOR));
    }

    @Override
    public String toString() {
        return "PendingKakaoSignup[emailVerified=" + emailVerified + ", createdAt=" + createdAt + "]";
    }
}
