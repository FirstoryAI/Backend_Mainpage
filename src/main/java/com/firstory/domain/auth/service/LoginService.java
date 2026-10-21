package com.firstory.domain.auth.service;

import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * 이메일과 비밀번호 확인, 로그인 잠금. 세션 처리는 LoginSuccessHandler가 한다.
 * 트랜잭션을 두지 않는다. 실패 횟수는 LoginAttemptRecorder가 따로 커밋한다.
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptRecorder loginAttemptRecorder;
    private final Clock clock;

    /**
     * 이메일이 없을 때와 비밀번호가 틀렸을 때 같은 오류를 낸다 (가입 여부 노출 방지).
     */
    public UserPrincipal authenticate(String email, String password) {
        String normalized = User.normalizeEmail(email);
        Optional<User> user = userRepository.findByEmail(normalized);
        if (user.isPresent() && user.get().isLocked(clock.instant())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        Authentication authentication;
        try {
            // 회원이 없어도 인증을 시도한다. DaoAuthenticationProvider가 비밀번호 비교 시간을 맞춰 준다
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalized, password));
        } catch (AuthenticationException e) {
            user.ifPresent(found -> loginAttemptRecorder.recordFailure(found.getId()));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        loginAttemptRecorder.recordSuccess(principal.getUserId());
        return principal;
    }
}
