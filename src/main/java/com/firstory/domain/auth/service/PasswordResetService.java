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

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PhoneVerificationService phoneVerificationService;
    private final PhoneCrypto phoneCrypto;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public String resetPassword(String phoneDisplay, String newPassword, HttpSession session) {
        PhoneNumber phone = PhoneNumber.fromDisplay(phoneDisplay);
        phoneVerificationService.requireVerified(session, phone, SmsPurpose.PASSWORD_RESET);

        User user = userRepository.findByPhoneHash(phoneCrypto.hash(phone))
                .orElseThrow(() -> new BusinessException(ErrorCode.PHONE_NOT_VERIFIED));

        PasswordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        user.recordLoginSuccess();
        return user.getEmail();
    }
}
