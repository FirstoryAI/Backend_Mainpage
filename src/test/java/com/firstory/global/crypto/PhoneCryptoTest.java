package com.firstory.global.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneCryptoTest {

    private static final String AES_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String HMAC_KEY = Base64.getEncoder().encodeToString("hmac-test-key".getBytes());

    private final PhoneCrypto crypto = new PhoneCrypto(AES_KEY, HMAC_KEY);
    private final PhoneNumber phone = PhoneNumber.fromDisplay("010-1234-5678");

    @Test
    void 암호화한_값을_복호화하면_원래_번호() {
        String encrypted = crypto.encrypt(phone);

        assertThat(encrypted).doesNotContain("01012345678");
        assertThat(crypto.decrypt(encrypted)).isEqualTo(phone);
    }

    @Test
    void 같은_번호도_암호문은_매번_다르다() {
        assertThat(crypto.encrypt(phone)).isNotEqualTo(crypto.encrypt(phone));
    }

    @Test
    void 해시는_같은_번호면_항상_같다() {
        String hash = crypto.hash(phone);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(crypto.hash(PhoneNumber.fromDisplay("010-1234-5678"))).isEqualTo(hash);
        assertThat(crypto.hash(PhoneNumber.fromDisplay("010-1234-5679"))).isNotEqualTo(hash);
    }

    @Test
    void AES_키가_32바이트가_아니면_시작하지_않는다() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new PhoneCrypto(shortKey, HMAC_KEY))
                .isInstanceOf(IllegalStateException.class);
    }
}
