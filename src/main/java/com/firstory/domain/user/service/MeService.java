package com.firstory.domain.user.service;

import com.firstory.domain.auth.entity.PasswordPolicy;
import com.firstory.domain.auth.entity.SmsPurpose;
import com.firstory.domain.auth.service.PhoneVerificationService;
import com.firstory.domain.user.dto.MeResponse;
import com.firstory.domain.user.entity.ConsentType;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.entity.UserConsent;
import com.firstory.domain.user.repository.UserConsentRepository;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.crypto.PhoneCrypto;
import com.firstory.global.crypto.PhoneNumber;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * 내 정보. 세션과 쿠키 처리는 MeController가 커밋 뒤에 한다.
 */
@Service
@RequiredArgsConstructor
public class MeService {

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final PhoneVerificationService phoneVerificationService;
    private final PhoneCrypto phoneCrypto;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public void markOnboardingSeen(Long userId) {
        findUser(userId).markOnboardingSeen(clock.instant());
    }

    /**
     * 동의하면 새 동의 행을 남기고, 철회하면 최신 행에 철회 시각을 남긴다.
     */
    @Transactional
    public void changeSmsNotify(Long userId, boolean agreed) {
        User user = findUser(userId);
        Optional<UserConsent> latest = latestSmsNotify(userId);
        boolean active = latest.map(UserConsent::isActive).orElse(false);
        if (agreed && !active) {
            // 약관 버전은 가입 때 남긴 행을 따른다. 가입 시 4가지 동의를 모두 기록하므로 행이 항상 있다
            String termsVersion = latest.map(UserConsent::getTermsVersion)
                    .orElseThrow(() -> new IllegalStateException("가입 동의 기록이 없어요."));
            userConsentRepository.save(
                    UserConsent.record(user, ConsentType.SMS_NOTIFY, true, termsVersion, clock.instant()));
        } else if (!agreed && active) {
            latest.get().withdraw(clock.instant());
        }
    }

    /**
     * @return 비밀번호를 바꾼 회원의 이메일. 호출한 쪽이 이 이메일의 모든 세션을 끊는다
     */
    @Transactional
    public String changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findUser(userId);
        checkCurrentPassword(user, currentPassword);
        PasswordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        return user.getEmail();
    }

    /**
     * 세션의 VerifiedPhone은 지우지 않는다. 커밋 뒤 호출한 쪽에서 지운다.
     */
    @Transactional
    public void changePhone(Long userId, String currentPassword, String phoneDisplay, HttpSession session) {
        User user = findUser(userId);
        checkCurrentPassword(user, currentPassword);

        PhoneNumber phone = PhoneNumber.fromDisplay(phoneDisplay);
        Instant verifiedAt = phoneVerificationService.requireVerified(session, phone, SmsPurpose.CHANGE_PHONE);
        String phoneHash = phoneCrypto.hash(phone);
        if (userRepository.existsByPhoneHash(phoneHash)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE);
        }

        user.changePhone(phoneCrypto.encrypt(phone), phoneHash, verifiedAt);
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 확인한 뒤 그사이 다른 회원이 같은 번호로 가입한 경우 (부분 UNIQUE 인덱스)
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE);
        }
    }

    /**
     * @return 탈퇴한 회원의 이메일. 호출한 쪽이 이 이메일의 모든 세션을 끊는다
     */
    @Transactional
    public String withdraw(Long userId, String currentPassword) {
        User user = findUser(userId);
        checkCurrentPassword(user, currentPassword);
        // TODO(PM): PRD 36 파기 정책. 아이, 동화 데이터와 휴대폰 번호를 언제 어떻게 지울지 미정. 지금은 회원만 소프트 삭제
        user.withdraw(clock.instant());
        return user.getEmail();
    }

    private void checkCurrentPassword(User user, String currentPassword) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
    }

    private MeResponse toResponse(User user) {
        boolean smsNotify = latestSmsNotify(user.getId()).map(UserConsent::isActive).orElse(false);
        return new MeResponse(user.getId(), user.getEmail(), phoneCrypto.decrypt(user.getPhoneEnc()).masked(),
                user.hasSeenOnboarding(), smsNotify);
    }

    private Optional<UserConsent> latestSmsNotify(Long userId) {
        return userConsentRepository.findTopByUserIdAndConsentTypeOrderByIdDesc(userId, ConsentType.SMS_NOTIFY);
    }

    // 탈퇴했거나 세션이 남아 있는 사이 지워진 회원이면 다시 로그인하게 한다
    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
