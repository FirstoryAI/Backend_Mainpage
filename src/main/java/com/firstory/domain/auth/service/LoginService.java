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

@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptRecorder loginAttemptRecorder;
    private final Clock clock;

    public UserPrincipal authenticate(String email, String password) {
        String normalized = User.normalizeEmail(email);
        Optional<User> user = userRepository.findByEmail(normalized);
        if (user.isPresent() && user.get().isLocked(clock.instant())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        Authentication authentication;
        try {
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
