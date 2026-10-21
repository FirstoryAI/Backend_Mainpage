package com.firstory.domain.auth.entity;

import com.firstory.global.crypto.PhoneNumber;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneVerificationTest {

    static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    static final PhoneNumber PHONE = PhoneNumber.fromDisplay("010-1234-5678");

    @RepeatedTest(20)
    void 인증번호는_6자리_숫자() {
        assertThat(PhoneVerification.newCode(new SecureRandom())).matches("^\\d{6}$");
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

        assertThat(PhoneVerification.newCode(fixed)).isEqualTo("000042");
    }

    @Test
    void 인증번호는_해시로만_저장하고_평문이_없다() {
        PhoneVerification verification = PhoneVerification.issue(PHONE, SmsPurpose.SIGNUP, "123456", NOW);

        assertThat(verification.codeHash()).hasSize(64).doesNotContain("123456");
        assertThat(verification.toString()).doesNotContain("123456").doesNotContain("5678");
        assertThat(verification.matches("123456")).isTrue();
        assertThat(verification.matches("654321")).isFalse();
        assertThat(verification.matches(null)).isFalse();
    }

    @Test
    void 발송_3분이_지나면_만료() {
        PhoneVerification verification = PhoneVerification.issue(PHONE, SmsPurpose.SIGNUP, "123456", NOW);

        assertThat(verification.isExpired(NOW.plus(Duration.ofMinutes(3)).minusSeconds(1))).isFalse();
        assertThat(verification.isExpired(NOW.plus(Duration.ofMinutes(3)))).isTrue();
    }

    @Test
    void 다섯_번_틀리면_시도_초과() {
        PhoneVerification verification = PhoneVerification.issue(PHONE, SmsPurpose.SIGNUP, "123456", NOW);
        for (int i = 0; i < 4; i++) {
            verification = verification.withFailedAttempt();
        }
        assertThat(verification.isAttemptsExceeded()).isFalse();

        assertThat(verification.withFailedAttempt().isAttemptsExceeded()).isTrue();
    }

    @Test
    void 인증한_번호는_같은_번호와_목적으로_10분_안에만_쓸_수_있다() {
        VerifiedPhone verified = VerifiedPhone.of(PHONE, SmsPurpose.SIGNUP, NOW);

        assertThat(verified.isUsableFor(PHONE, SmsPurpose.SIGNUP, NOW.plus(Duration.ofMinutes(9)))).isTrue();
        assertThat(verified.isUsableFor(PHONE, SmsPurpose.SIGNUP, NOW.plus(Duration.ofMinutes(10)))).isFalse();
        assertThat(verified.isUsableFor(PHONE, SmsPurpose.CHANGE_PHONE, NOW)).isFalse();
        assertThat(verified.isUsableFor(PhoneNumber.fromDisplay("010-0000-0000"), SmsPurpose.SIGNUP, NOW)).isFalse();
    }
}
