package com.firstory.domain.auth.service;

import com.firstory.domain.auth.entity.PhoneVerification;
import com.firstory.domain.auth.entity.SmsPurpose;
import com.firstory.domain.auth.entity.SmsSendLog;
import com.firstory.domain.auth.entity.VerifiedPhone;
import com.firstory.domain.auth.repository.SmsSendLogRepository;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.crypto.PhoneCrypto;
import com.firstory.global.crypto.PhoneNumber;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.sms.SmsSender;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 문자 인증. 인증번호와 인증 결과는 세션에, 발송 기록은 sms_send_logs에 둔다.
 */
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    static final String VERIFICATION_ATTRIBUTE = PhoneVerification.class.getName();
    static final String VERIFIED_ATTRIBUTE = VerifiedPhone.class.getName();

    static final int HOURLY_SEND_LIMIT = 5;
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);

    private final SmsSendLogRepository smsSendLogRepository;
    private final UserRepository userRepository;
    private final PhoneCrypto phoneCrypto;
    private final SmsSender smsSender;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * @return 인증번호 유효 시간
     */
    @Transactional
    public Duration sendCode(HttpSession session, PhoneNumber phone, SmsPurpose purpose) {
        String phoneHash = phoneCrypto.hash(phone);
        if (purpose == SmsPurpose.SIGNUP && userRepository.existsByPhoneHash(phoneHash)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE);
        }

        Instant now = clock.instant();
        if (smsSendLogRepository.countByPhoneHashAndCreatedAtAfter(phoneHash, now.minus(Duration.ofHours(1)))
                >= HOURLY_SEND_LIMIT) {
            throw new BusinessException(ErrorCode.SMS_RATE_LIMITED);
        }
        if (smsSendLogRepository.existsByPhoneHashAndCreatedAtAfter(phoneHash, now.minus(RESEND_INTERVAL))) {
            throw new BusinessException(ErrorCode.SMS_RESEND_TOO_SOON);
        }

        String code = PhoneVerification.newCode(random);
        session.setAttribute(VERIFICATION_ATTRIBUTE, PhoneVerification.issue(phone, purpose, code, now));
        smsSendLogRepository.save(SmsSendLog.record(phoneHash, purpose));
        smsSender.send(phone, "[FIRSTORY] 인증번호 " + code + " (3분 안에 입력해 주세요)");
        return PhoneVerification.VALIDITY;
    }

    public void verify(HttpSession session, PhoneNumber phone, String code) {
        PhoneVerification verification = (PhoneVerification) session.getAttribute(VERIFICATION_ATTRIBUTE);
        Instant now = clock.instant();
        if (verification == null || !verification.isFor(phone) || verification.isExpired(now)) {
            throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        }
        if (verification.isAttemptsExceeded()) {
            throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (!verification.matches(code)) {
            PhoneVerification failed = verification.withFailedAttempt();
            session.setAttribute(VERIFICATION_ATTRIBUTE, failed);
            throw new BusinessException(failed.isAttemptsExceeded()
                    ? ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED
                    : ErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        session.removeAttribute(VERIFICATION_ATTRIBUTE);
        session.setAttribute(VERIFIED_ATTRIBUTE, VerifiedPhone.of(phone, verification.purpose(), now));
    }

    /**
     * 세션에 이 번호와 목적으로 10분 안에 인증한 기록이 있는지 확인한다. 다 쓴 뒤에는 clearVerified를 부른다.
     *
     * @return 인증한 시각
     */
    public Instant requireVerified(HttpSession session, PhoneNumber phone, SmsPurpose purpose) {
        VerifiedPhone verified = (VerifiedPhone) session.getAttribute(VERIFIED_ATTRIBUTE);
        if (verified == null || !verified.isUsableFor(phone, purpose, clock.instant())) {
            throw new BusinessException(ErrorCode.PHONE_NOT_VERIFIED);
        }
        return verified.verifiedAt();
    }

    public void clearVerified(HttpSession session) {
        session.removeAttribute(VERIFIED_ATTRIBUTE);
    }
}
