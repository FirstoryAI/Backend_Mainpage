package com.firstory.domain.auth.service;

import com.firstory.domain.auth.dto.SignupRequest;
import com.firstory.domain.auth.entity.PasswordPolicy;
import com.firstory.domain.auth.entity.SmsPurpose;
import com.firstory.domain.user.entity.ConsentPolicy;
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
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SignupService {

    private static final String EMAIL_UNIQUE_INDEX = "ux_users_email_active";
    private static final String PHONE_UNIQUE_INDEX = "ux_users_phone_hash_active";

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final PhoneVerificationService phoneVerificationService;
    private final PhoneCrypto phoneCrypto;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * 세션의 VerifiedPhone은 지우지 않는다. 커밋 뒤 호출한 쪽에서 지운다.
     */
    @Transactional
    public User signup(SignupRequest request, HttpSession session) {
        PasswordPolicy.validate(request.password());
        ConsentPolicy.validateRequired(request.consents());

        PhoneNumber phone = PhoneNumber.fromDisplay(request.phone());
        Instant verifiedAt = phoneVerificationService.requireVerified(session, phone, SmsPurpose.SIGNUP);

        String email = User.normalizeEmail(request.email());
        String phoneHash = phoneCrypto.hash(phone);
        // 인증한 뒤 그사이 누가 같은 번호로 가입했을 수 있어 번호도 다시 확인한다
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByPhoneHash(phoneHash)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE);
        }

        User user = User.signUp(email, passwordEncoder.encode(request.password()), phoneCrypto.encrypt(phone),
                phoneHash, verifiedAt);
        saveUnique(user);

        Instant now = clock.instant();
        for (ConsentType type : ConsentType.values()) {
            boolean agreed = Boolean.TRUE.equals(request.consents().get(type));
            userConsentRepository.save(UserConsent.record(user, type, agreed, request.termsVersion(), now));
        }
        return user;
    }

    // 동시에 같은 이메일이나 번호로 가입하면 위의 중복 확인을 둘 다 통과할 수 있다. DB 부분 UNIQUE 인덱스가 막는다
    private void saveUnique(User user) {
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
            if (cause.contains(EMAIL_UNIQUE_INDEX)) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            if (cause.contains(PHONE_UNIQUE_INDEX)) {
                throw new BusinessException(ErrorCode.DUPLICATE_PHONE);
            }
            throw e;
        }
    }
}
