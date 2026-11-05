package com.firstory.domain.auth.service;

import com.firstory.domain.auth.entity.EmailPurpose;
import com.firstory.domain.auth.entity.EmailSendLog;
import com.firstory.domain.auth.entity.EmailVerification;
import com.firstory.domain.auth.entity.VerifiedEmail;
import com.firstory.domain.auth.repository.EmailSendLogRepository;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.config.FirstoryProperties.Mail.MailMode;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.mail.EmailSender;
import com.firstory.global.mail.MailSendException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 이메일 인증번호. 발송 기록 저장은 트랜잭션 안에서, 메일 발송은 트랜잭션 밖에서 한다 (SMTP가 수 초 걸릴 수 있다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final String VERIFICATION_ATTRIBUTE = EmailVerification.class.getName();
    static final String VERIFIED_ATTRIBUTE = VerifiedEmail.class.getName();

    static final int HOURLY_SEND_LIMIT = 5;
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    static final String SUBJECT = "[FIRSTORY] 인증번호를 알려 드려요";

    private final EmailSendLogRepository emailSendLogRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final ObjectProvider<LocalFixedVerificationCode> localFixedCode;
    private final FirstoryProperties properties;
    private final SecureRandom random = new SecureRandom();

    /**
     * @return 인증번호 유효 시간
     */
    public Duration sendCode(HttpSession session, String rawEmail, EmailPurpose purpose) {
        String email = User.normalizeEmail(rawEmail);
        Instant now = clock.instant();
        boolean registered = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            boolean exists = userRepository.existsByEmail(email);
            if (purpose == EmailPurpose.SIGNUP && exists) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            String emailHash = EmailVerification.sha256(email);
            if (emailSendLogRepository.countByEmailHashAndCreatedAtAfter(emailHash, now.minus(Duration.ofHours(1)))
                    >= HOURLY_SEND_LIMIT) {
                throw new BusinessException(ErrorCode.VERIFICATION_RATE_LIMITED);
            }
            if (emailSendLogRepository.existsByEmailHashAndCreatedAtAfter(emailHash, now.minus(RESEND_INTERVAL))) {
                throw new BusinessException(ErrorCode.VERIFICATION_RESEND_TOO_SOON);
            }
            emailSendLogRepository.save(EmailSendLog.record(emailHash, purpose));
            return exists;
        }));

        // 가입하지 않은 이메일의 비밀번호 재설정은 메일을 보내지 않지만 응답은 같다 (가입 여부를 드러내지 않는다)
        if (purpose == EmailPurpose.PASSWORD_RESET && !registered) {
            session.setAttribute(VERIFICATION_ATTRIBUTE,
                    EmailVerification.issueUnmatchable(email, purpose, random, now));
            return EmailVerification.VALIDITY;
        }

        String code = newCode();
        try {
            emailSender.send(email, SUBJECT, text(code));
        } catch (MailSendException e) {
            log.warn("인증 메일을 보내지 못했어요: purpose={}, cause={}", purpose, e.getMessage());
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
        session.setAttribute(VERIFICATION_ATTRIBUTE, EmailVerification.issue(email, purpose, code, now));
        return EmailVerification.VALIDITY;
    }

    private static String text(String code) {
        return """
                FIRSTORY 인증번호는 %s 예요.
                5분 안에 화면에 입력해 주세요.

                요청하지 않았다면 이 메일은 지워도 괜찮아요.""".formatted(code);
    }

    /**
     * 고정 인증번호(local, dev)는 메일을 보내지 않을 때(mode=log)만 쓴다. 실제로 메일을 보내면 무작위 번호다
     * (dev 서버에서 Gmail을 켜면 000000으로 아무 이메일이나 인증되지 않게).
     */
    private String newCode() {
        if (properties.mail().mode() != MailMode.LOG) {
            return EmailVerification.newCode(random);
        }
        LocalFixedVerificationCode fixed = localFixedCode.getIfAvailable();
        return fixed == null ? EmailVerification.newCode(random)
                : fixed.code().orElseGet(() -> EmailVerification.newCode(random));
    }

    public void verify(HttpSession session, String rawEmail, String code) {
        String email = User.normalizeEmail(rawEmail);
        EmailVerification verification = (EmailVerification) session.getAttribute(VERIFICATION_ATTRIBUTE);
        Instant now = clock.instant();
        if (verification == null || !verification.isFor(email) || verification.isExpired(now)) {
            throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        }
        if (verification.isAttemptsExceeded()) {
            throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (!verification.matches(code)) {
            EmailVerification failed = verification.withFailedAttempt();
            session.setAttribute(VERIFICATION_ATTRIBUTE, failed);
            throw new BusinessException(failed.isAttemptsExceeded()
                    ? ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED
                    : ErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        session.removeAttribute(VERIFICATION_ATTRIBUTE);
        session.setAttribute(VERIFIED_ATTRIBUTE, new VerifiedEmail(email, verification.purpose(), now));
    }

    /**
     * @param email 소문자로 맞춘 이메일
     * @return 인증한 시각
     */
    public Instant requireVerified(HttpSession session, String email, EmailPurpose purpose) {
        VerifiedEmail verified = (VerifiedEmail) session.getAttribute(VERIFIED_ATTRIBUTE);
        if (verified == null || !verified.isUsableFor(email, purpose, clock.instant())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        return verified.verifiedAt();
    }

    public void clearVerified(HttpSession session) {
        session.removeAttribute(VERIFIED_ATTRIBUTE);
    }
}
