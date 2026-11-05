package com.firstory.domain.auth.entity;

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
 * 세션에 넣는 발송한 인증번호. 번호는 SHA-256 해시만 남긴다. email은 소문자로 맞춘 값이다.
 */
public record EmailVerification(String email, EmailPurpose purpose, String codeHash, Instant expiresAt,
                                int attempts) implements Serializable {

    public static final Duration VALIDITY = Duration.ofMinutes(5);
    public static final int MAX_ATTEMPTS = 5;
    private static final int CODE_BOUND = 1_000_000;

    public EmailVerification {
        Objects.requireNonNull(email);
        Objects.requireNonNull(purpose);
        Objects.requireNonNull(codeHash);
        Objects.requireNonNull(expiresAt);
    }

    public static String newCode(RandomGenerator random) {
        return String.format("%06d", random.nextInt(CODE_BOUND));
    }

    public static EmailVerification issue(String email, EmailPurpose purpose, String code, Instant now) {
        return new EmailVerification(email, purpose, sha256(code), now.plus(VALIDITY), 0);
    }

    /**
     * 가입하지 않은 이메일의 비밀번호 재설정. 메일은 보내지 않고, 어떤 번호로도 맞지 않는 인증을 남긴다
     * (가입 여부를 응답으로 드러내지 않으려고).
     */
    public static EmailVerification issueUnmatchable(String email, EmailPurpose purpose, RandomGenerator random,
                                                     Instant now) {
        String neverSent = "x" + Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong());
        return new EmailVerification(email, purpose, sha256(neverSent), now.plus(VALIDITY), 0);
    }

    public boolean isFor(String email) {
        return this.email.equals(email);
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
        return MessageDigest.isEqual(sha256(code.trim()).getBytes(StandardCharsets.US_ASCII),
                codeHash.getBytes(StandardCharsets.US_ASCII));
    }

    public EmailVerification withFailedAttempt() {
        return new EmailVerification(email, purpose, codeHash, expiresAt, attempts + 1);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "EmailVerification[purpose=" + purpose + ", expiresAt=" + expiresAt + ", attempts=" + attempts + "]";
    }
}
