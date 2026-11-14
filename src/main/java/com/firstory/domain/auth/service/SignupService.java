package com.firstory.domain.auth.service;
import com.firstory.domain.auth.dto.SignupRequest;
import com.firstory.domain.auth.entity.EmailPurpose;
import com.firstory.domain.auth.entity.PasswordPolicy;
import com.firstory.domain.user.entity.ConsentPolicy;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * 가입. 이메일 + 비밀번호 가입과 카카오 가입(KakaoLoginService)이 회원 저장과 동의 기록을 함께 쓴다.
 */
@Service
@RequiredArgsConstructor
public class SignupService {
    private static final String EMAIL_UNIQUE_INDEX = "ux_users_email_active";
    private static final String KAKAO_UNIQUE_INDEX = "ux_users_kakao_id_active";
    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;
    @Transactional
    public User signup(SignupRequest request, HttpSession session) {
        PasswordPolicy.validate(request.password());
        ConsentPolicy.validateRequired(request.consents());
        String email = User.normalizeEmail(request.email());
        emailVerificationService.requireVerified(session, email, EmailPurpose.SIGNUP);
        // 인증한 뒤 그사이 누가 같은 이메일로 가입했을 수 있어 다시 확인한다
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        User user = User.signUp(email, passwordEncoder.encode(request.password()),
                ConsentPolicy.emailNotify(request.consents()));
        saveUnique(user);
        return user;
    }

    /**
     * 같은 이메일로 동시에 가입하면 DB 부분 UNIQUE가 막는다. 카카오 가입도 이걸로 저장한다.
     */
    @Transactional
    public void saveUnique(User user) {
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
            if (cause.contains(EMAIL_UNIQUE_INDEX)) {
                throw new BusinessException(user.isKakaoLinked() ? ErrorCode.KAKAO_EMAIL_REGISTERED
                        : ErrorCode.DUPLICATE_EMAIL);
            }
            if (cause.contains(KAKAO_UNIQUE_INDEX)) {
                throw new BusinessException(ErrorCode.KAKAO_ACCOUNT_REGISTERED);
            }
            throw e;
        }
    }
}
