package com.firstory.domain.user.service;

import com.firstory.domain.auth.entity.PasswordPolicy;
import com.firstory.domain.user.dto.MeResponse;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 내 정보. 세션과 쿠키 처리는 MeController가 커밋 뒤에 한다.
 */
@Service
@RequiredArgsConstructor
public class MeService {

    private final UserRepository userRepository;
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

    @Transactional
    public void changeEmailNotify(Long userId, boolean agreed) {
        findUser(userId).changeEmailNotify(agreed);
    }

    /**
     * @return 비밀번호를 바꾼 회원의 이메일. 호출한 쪽이 이 이메일의 모든 세션을 끊는다
     */
    @Transactional
    public String changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findUser(userId);
        if (!user.hasPassword()) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_SET);
        }
        checkCurrentPassword(user, currentPassword);
        PasswordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        return user.getEmail();
    }

    /**
     * @return 탈퇴한 회원의 이메일. 호출한 쪽이 이 이메일의 모든 세션을 끊는다
     */
    @Transactional
    public String withdraw(Long userId, String currentPassword) {
        User user = findUser(userId);
        // 카카오로만 가입해 비밀번호가 없는 회원은 로그인한 세션만으로 탈퇴한다
        if (user.hasPassword()) {
            checkCurrentPassword(user, currentPassword);
        }
        // TODO(PM): PRD 36 파기 정책. 아이, 동화 데이터와 이메일을 언제 어떻게 지울지 미정. 지금은 회원만 소프트 삭제
        user.withdraw(clock.instant());
        return user.getEmail();
    }

    private void checkCurrentPassword(User user, String currentPassword) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
    }

    private MeResponse toResponse(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.hasSeenOnboarding(), user.isEmailNotify(),
                user.isKakaoLinked(), user.hasPassword());
    }


    // 탈퇴했거나 세션이 남아 있는 사이 지워진 회원이면 다시 로그인하게 한다
    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
