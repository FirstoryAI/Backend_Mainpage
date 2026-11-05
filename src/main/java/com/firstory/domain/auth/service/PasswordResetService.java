package com.firstory.domain.auth.service;

import com.firstory.domain.auth.entity.EmailPurpose;
import com.firstory.domain.auth.entity.PasswordPolicy;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정. 비밀번호가 없는 카카오 회원은 여기서 비밀번호를 처음 만든다 (그 뒤 이메일로도 로그인할 수 있다).
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;

    /**
     * @return 비밀번호를 바꾼 회원의 이메일. 호출한 쪽이 이 이메일의 모든 세션을 끊는다
     */
    @Transactional
    public String resetPassword(String rawEmail, String newPassword, HttpSession session) {
        String email = User.normalizeEmail(rawEmail);
        emailVerificationService.requireVerified(session, email, EmailPurpose.PASSWORD_RESET);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));

        PasswordPolicy.validate(newPassword);
        if (user.hasPassword() && passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        user.recordLoginSuccess();
        return user.getEmail();
    }
}
