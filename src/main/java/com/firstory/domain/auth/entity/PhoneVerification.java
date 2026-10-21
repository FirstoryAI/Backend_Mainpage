package com.firstory.domain.auth.entity;

import com.firstory.global.crypto.PhoneNumber;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * 발송한 인증번호. 세션에 저장한다 (Spring Session JDBC가 직렬화).
 * 인증번호 평문은 두지 않고 SHA-256 해시만 둔다.
 */
public record PhoneVerification(String phoneDigits, SmsPurpose purpose, String codeHash, Instant expiresAt,
                                int attempts) implements Serializable {

    public static final Duration VALIDITY = Duration.ofMinutes(3);
    public static final int MAX_ATTEMPTS = 5;
    private static final int CODE_BOUND = 1_000_000;

    public PhoneVerification {
        Objects.requireNonNull(phoneDigits);
        Objects.requireNonNull(purpose);
        Objects.requireNonNull(codeHash);
        Objects.requireNonNull(expiresAt);
    }

    /**
     * 6자리 숫자. 앞자리 0도 그대로 둔다.
     */
    public static String newCode(RandomGenerator random) {
        return String.format("%06d", random.nextInt(CODE_BOUND));
    }

    public static PhoneVerification issue(PhoneNumber phone, SmsPurpose purpose, String code, Instant now) {
        return new PhoneVerification(phone.digits(), purpose, hash(code), now.plus(VALIDITY), 0);
    }

    public boolean isFor(PhoneNumber phone) {
        return phoneDigits.equals(phone.digits());
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isAttemptsExceeded() {
        return attempts >= MAX_ATTEMPTS;
    }

    public boolean matches(String code) {
        if (code == null) {
            return false;
        }
        return MessageDigest.isEqual(hash(code.trim()).getBytes(StandardCharsets.US_ASCII),
                codeHash.getBytes(StandardCharsets.US_ASCII));
    }

    public PhoneVerification withFailedAttempt() {
        return new PhoneVerification(phoneDigits, purpose, codeHash, expiresAt, attempts + 1);
    }

    private static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // 번호가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "PhoneVerification[purpose=" + purpose + ", expiresAt=" + expiresAt + ", attempts=" + attempts + "]";
    }
}
