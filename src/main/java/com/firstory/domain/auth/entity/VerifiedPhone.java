package com.firstory.domain.auth.entity;

import com.firstory.global.crypto.PhoneNumber;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 인증을 마친 번호. 세션에 저장하고 10분 안에 가입이나 번호 변경에 한 번만 쓴다.
 */
public record VerifiedPhone(String phoneDigits, SmsPurpose purpose, Instant verifiedAt) implements Serializable {

    public static final Duration USABLE_FOR = Duration.ofMinutes(10);

    public VerifiedPhone {
        Objects.requireNonNull(phoneDigits);
        Objects.requireNonNull(purpose);
        Objects.requireNonNull(verifiedAt);
    }

    public static VerifiedPhone of(PhoneNumber phone, SmsPurpose purpose, Instant now) {
        return new VerifiedPhone(phone.digits(), purpose, now);
    }

    public boolean isUsableFor(PhoneNumber phone, SmsPurpose purpose, Instant now) {
        return phoneDigits.equals(phone.digits())
                && this.purpose == purpose
                && now.isBefore(verifiedAt.plus(USABLE_FOR));
    }

    // 번호가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "VerifiedPhone[purpose=" + purpose + ", verifiedAt=" + verifiedAt + "]";
    }
}
