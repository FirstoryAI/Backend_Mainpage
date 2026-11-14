package com.firstory.domain.auth.service;

import com.firstory.domain.auth.dto.KakaoSignupRequest;
import com.firstory.domain.auth.entity.EmailPurpose;
import com.firstory.domain.auth.entity.PendingKakaoSignup;
import com.firstory.domain.user.entity.ConsentPolicy;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.kakao.KakaoClient;
import com.firstory.global.kakao.KakaoUser;
import com.firstory.global.security.UserPrincipal;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * 카카오 로그인과 카카오 가입.
 * <ol>
 *   <li>로그인: 카카오 회원번호로 가입한 회원이 있으면 그 회원으로 로그인한다</li>
 *   <li>없으면 카카오 이메일로 이미 가입한 회원이 있는지 본다. 있으면 KAKAO_EMAIL_REGISTERED (계정을 합치지 않는다)</li>
 *   <li>없으면 세션에 가입 전 카카오 회원을 남기고 가입 화면(동의)으로 보낸다</li>
 *   <li>가입: 카카오가 인증한 이메일이면 그대로, 아니면 우리 이메일 인증(SIGNUP)을 마친 이메일로 만든다. 비밀번호는 없다</li>
 * </ol>
 * 카카오 호출은 트랜잭션 밖에서 한다.
 */
@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    static final String PENDING_ATTRIBUTE = PendingKakaoSignup.class.getName();

    private final KakaoClient kakaoClient;
    private final UserRepository userRepository;
    private final SignupService signupService;
    private final EmailVerificationService emailVerificationService;
    private final FirstoryProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * @return 가입한 회원이면 그 회원. 처음 본 카카오 회원이면 빈 값 (세션에 가입 전 회원을 남긴다)
     */
    public Optional<UserPrincipal> login(String code, String redirectUri, HttpSession session) {
        checkRedirectUri(redirectUri);
        KakaoUser kakaoUser = kakaoClient.fetchUser(code, redirectUri);
        String email = kakaoUser.email() == null ? null : User.normalizeEmail(kakaoUser.email());

        Optional<UserPrincipal> existing = transactionTemplate.execute(status -> {
            Optional<User> user = userRepository.findByKakaoId(kakaoUser.id());
            if (user.isPresent()) {
                return user.map(UserPrincipal::from);
            }
            if (email != null && userRepository.existsByEmail(email)) {
                throw new BusinessException(ErrorCode.KAKAO_EMAIL_REGISTERED);
            }
            return Optional.<UserPrincipal>empty();
        });
        if (existing.isPresent()) {
            session.removeAttribute(PENDING_ATTRIBUTE);
            return existing;
        }
        session.setAttribute(PENDING_ATTRIBUTE,
                new PendingKakaoSignup(kakaoUser.id(), email, kakaoUser.emailVerified(), clock.instant()));
        return Optional.empty();
    }

    public PendingKakaoSignup pending(HttpSession session) {
        PendingKakaoSignup pending = (PendingKakaoSignup) session.getAttribute(PENDING_ATTRIBUTE);
        if (pending == null || !pending.isUsable(clock.instant())) {
            throw new BusinessException(ErrorCode.KAKAO_SIGNUP_EXPIRED);
        }
        return pending;
    }

    public User signup(KakaoSignupRequest request, HttpSession session) {
        PendingKakaoSignup pending = pending(session);
        ConsentPolicy.validateRequired(request.consents());

        User user = transactionTemplate.execute(status -> {
            String email;
            if (pending.emailVerified()) {
                email = pending.email();
            } else {
                if (request.email() == null || request.email().isBlank()) {
                    throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
                }
                email = User.normalizeEmail(request.email());
                emailVerificationService.requireVerified(session, email, EmailPurpose.SIGNUP);
            }
            if (userRepository.existsByKakaoId(pending.kakaoId())) {
                throw new BusinessException(ErrorCode.KAKAO_ACCOUNT_REGISTERED);
            }
            if (userRepository.existsByEmail(email)) {
                throw new BusinessException(ErrorCode.KAKAO_EMAIL_REGISTERED);
            }
            User created = User.signUpWithKakao(email, pending.kakaoId(), ConsentPolicy.emailNotify(request.consents()));
            signupService.saveUnique(created);
            return created;
        });

        session.removeAttribute(PENDING_ATTRIBUTE);
        emailVerificationService.clearVerified(session);
        return user;
    }

    private void checkRedirectUri(String redirectUri) {
        List<String> allowed = properties.kakao().redirectUris();
        if (!allowed.isEmpty() && !allowed.contains(redirectUri)) {
            throw new BusinessException(ErrorCode.INVALID_KAKAO_REDIRECT_URI);
        }
    }
}
