package com.firstory.domain.auth.service;

import com.firstory.domain.auth.entity.PasswordPolicy;
import com.firstory.domain.auth.entity.SmsPurpose;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.crypto.PhoneCrypto;
import com.firstory.global.crypto.PhoneNumber;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호를 잊은 회원이 휴대폰 인증(purpose PASSWORD_RESET)으로 새 비밀번호를 정한다.
 * 가입 여부는 인증번호를 받은 사람(번호 주인)에게만 드러난다. 가입되지 않은 번호로는 문자가 가지 않아 인증을 통과할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PhoneVerificationService phoneVerificationService;
    private final PhoneCrypto phoneCrypto;
    private final PasswordEncoder passwordEncoder;

    /**
     * 세션의 VerifiedPhone은 지우지 않는다. 커밋 뒤 호출한 쪽에서 지운다.
     *
     * @return 비밀번호를 바꾼 회원의 이메일. 호출한 쪽이 이 이메일의 모든 세션을 끊는다
     */
    @Transactional
    public String resetPassword(String phoneDisplay, String newPassword, HttpSession session) {
        PhoneNumber phone = PhoneNumber.fromDisplay(phoneDisplay);
        // 인증부터 본다. 인증하지 않은 요청에는 비밀번호 규칙 오류도 주지 않는다
        phoneVerificationService.requireVerified(session, phone, SmsPurpose.PASSWORD_RESET);

        // 인증한 사이 탈퇴했거나 번호를 바꾼 경우. 인증을 마친 번호 주인에게만 닿는 응답이다
        User user = userRepository.findByPhoneHash(phoneCrypto.hash(phone))
                .orElseThrow(() -> new BusinessException(ErrorCode.PHONE_NOT_VERIFIED));

        PasswordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        // 비밀번호를 잊어 잠긴 계정도 새 비밀번호로 바로 로그인할 수 있게 한다
        user.recordLoginSuccess();
        return user.getEmail();
    }
}
