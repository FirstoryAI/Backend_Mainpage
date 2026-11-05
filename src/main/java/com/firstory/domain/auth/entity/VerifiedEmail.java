package com.firstory.domain.auth.entity;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 세션에 넣는 인증을 마친 이메일. 가입, 비밀번호 재설정에 한 번 쓰고 지운다.
 */
public record VerifiedEmail(String email, EmailPurpose purpose, Instant verifiedAt) implements Serializable {

    public static final Duration USABLE_FOR = Duration.ofMinutes(10);

    public VerifiedEmail {
        Objects.requireNonNull(email);
        Objects.requireNonNull(purpose);
        Objects.requireNonNull(verifiedAt);
    }

    public boolean isUsableFor(String email, EmailPurpose purpose, Instant now) {
        return this.email.equals(email)
                && this.purpose == purpose
                && now.isBefore(verifiedAt.plus(USABLE_FOR));
    }

    @Override
    public String toString() {
        return "VerifiedEmail[purpose=" + purpose + ", verifiedAt=" + verifiedAt + "]";
    }
}
