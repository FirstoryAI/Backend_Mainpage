package com.firstory.domain.auth.service;

import com.firstory.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 로그인 실패·성공 기록. 인증 실패 예외가 나도 실패 횟수는 커밋되도록 따로 트랜잭션을 연다.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptRecorder {

    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId) {
        userRepository.findById(userId).ifPresent(user -> user.recordLoginFailure(clock.instant()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId) {
        userRepository.findById(userId).ifPresent(user -> user.recordLoginSuccess());
    }
}
