package com.firstory.domain.auth.entity;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationTest {

    static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    static final String EMAIL = "parent@example.com";

    @RepeatedTest(20)
    void 인증번호는_6자리_숫자() {
        assertThat(EmailVerification.newCode(new SecureRandom())).matches("^\\d{6}$");
    }

    @Test
    void 앞자리가_0이어도_6자리를_유지한다() {
        RandomGenerator fixed = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return 42;
            }
        };

        assertThat(EmailVerification.newCode(fixed)).isEqualTo("000042");
    }

    @Test
    void 보내지_않은_인증번호는_어떤_6자리와도_맞지_않는다() {
        RandomGenerator zero = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }
        };
        EmailVerification unmatchable =
                EmailVerification.issueUnmatchable(EMAIL, EmailPurpose.PASSWORD_RESET, zero, NOW);

        assertThat(unmatchable.matches("000000")).isFalse();
        assertThat(unmatchable.matches("0")).isFalse();
        assertThat(unmatchable.matches("")).isFalse();
        assertThat(unmatchable.isFor(EMAIL)).isTrue();
        assertThat(unmatchable.expiresAt()).isEqualTo(NOW.plus(EmailVerification.VALIDITY));
    }

    @Test
    void 인증번호는_해시로만_저장하고_평문이_없다() {
        EmailVerification verification = EmailVerification.issue(EMAIL, EmailPurpose.SIGNUP, "123456", NOW);

        assertThat(verification.codeHash()).hasSize(64).doesNotContain("123456");
        assertThat(verification.toString()).doesNotContain("123456").doesNotContain("parent");
        assertThat(verification.matches("123456")).isTrue();
        assertThat(verification.matches("654321")).isFalse();
        assertThat(verification.matches(null)).isFalse();
    }

    @Test
    void 발송_5분이_지나면_만료() {
        EmailVerification verification = EmailVerification.issue(EMAIL, EmailPurpose.SIGNUP, "123456", NOW);

        assertThat(verification.isExpired(NOW.plus(Duration.ofMinutes(5)).minusSeconds(1))).isFalse();
        assertThat(verification.isExpired(NOW.plus(Duration.ofMinutes(5)))).isTrue();
    }

    @Test
    void 다섯_번_틀리면_시도_초과() {
        EmailVerification verification = EmailVerification.issue(EMAIL, EmailPurpose.SIGNUP, "123456", NOW);
        for (int i = 0; i < 4; i++) {
            verification = verification.withFailedAttempt();
        }
        assertThat(verification.isAttemptsExceeded()).isFalse();

        assertThat(verification.withFailedAttempt().isAttemptsExceeded()).isTrue();
    }

    @Test
    void 인증한_이메일은_같은_이메일과_목적으로_10분_안에만_쓸_수_있다() {
        VerifiedEmail verified = new VerifiedEmail(EMAIL, EmailPurpose.SIGNUP, NOW);

        assertThat(verified.isUsableFor(EMAIL, EmailPurpose.SIGNUP, NOW.plus(Duration.ofMinutes(9)))).isTrue();
        assertThat(verified.isUsableFor(EMAIL, EmailPurpose.SIGNUP, NOW.plus(Duration.ofMinutes(10)))).isFalse();
        assertThat(verified.isUsableFor(EMAIL, EmailPurpose.PASSWORD_RESET, NOW)).isFalse();
        assertThat(verified.isUsableFor("other@example.com", EmailPurpose.SIGNUP, NOW)).isFalse();
    }
}
