package com.firstory.global.crypto;

import com.firstory.global.config.FirstoryProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 휴대폰 번호 암호화(AES-256-GCM)와 중복 체크용 해시(HMAC-SHA256).
 */
@Component
public class PhoneCrypto {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String HMAC = "HmacSHA256";
    private static final int AES_KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public PhoneCrypto(FirstoryProperties properties) {
        this(properties.crypto().phoneAesKey(), properties.crypto().phoneHmacKey());
    }

    public PhoneCrypto(String aesKeyBase64, String hmacKeyBase64) {
        byte[] aes = decodeKey(aesKeyBase64, "phone-aes-key");
        if (aes.length != AES_KEY_BYTES) {
            throw new IllegalStateException("firstory.crypto.phone-aes-key는 32바이트여야 해요.");
        }
        byte[] hmac = decodeKey(hmacKeyBase64, "phone-hmac-key");
        if (hmac.length == 0) {
            throw new IllegalStateException("firstory.crypto.phone-hmac-key가 비어 있어요.");
        }
        this.aesKey = new SecretKeySpec(aes, "AES");
        this.hmacKey = new SecretKeySpec(hmac, HMAC);
    }

    public String encrypt(PhoneNumber phone) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(phone.digits().getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("휴대폰 번호 암호화에 실패했어요.", e);
        }
    }

    public PhoneNumber decrypt(String stored) {
        try {
            byte[] in = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new PhoneNumber(new String(plain, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("휴대폰 번호 복호화에 실패했어요.", e);
        }
    }

    public String hash(PhoneNumber phone) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(phone.digits().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("휴대폰 번호 해시에 실패했어요.", e);
        }
    }

    private static byte[] decodeKey(String base64, String name) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("firstory.crypto." + name + "가 설정되지 않았어요.");
        }
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("firstory.crypto." + name + "는 Base64여야 해요.", e);
        }
    }
}
