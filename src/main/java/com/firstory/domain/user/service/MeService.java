package com.firstory.domain.user.service;

import com.firstory.domain.user.dto.MeResponse;
import com.firstory.domain.user.entity.ConsentType;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.entity.UserConsent;
import com.firstory.domain.user.repository.UserConsentRepository;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.crypto.PhoneCrypto;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeService {

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final PhoneCrypto phoneCrypto;

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        return toResponse(findUser(userId));
    }

    private MeResponse toResponse(User user) {
        boolean smsNotify = userConsentRepository
                .findTopByUserIdAndConsentTypeOrderByIdDesc(user.getId(), ConsentType.SMS_NOTIFY)
                .map(UserConsent::isActive)
                .orElse(false);
        return new MeResponse(user.getId(), user.getEmail(), phoneCrypto.decrypt(user.getPhoneEnc()).masked(),
                user.hasSeenOnboarding(), smsNotify);
    }

    // 탈퇴했거나 세션이 남아 있는 사이 지워진 회원이면 다시 로그인하게 한다
    User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
