package com.firstory.domain.auth.entity;

import com.firstory.global.crypto.PhoneNumber;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

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

    @Override
    public String toString() {
        return "VerifiedPhone[purpose=" + purpose + ", verifiedAt=" + verifiedAt + "]";
    }
}
