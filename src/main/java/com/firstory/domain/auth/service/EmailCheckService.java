package com.firstory.domain.auth.service;

import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 정보 입력 단계(AUTH-02)에서 이메일이 이미 가입됐는지 미리 알려 준다.
 * 가입(signup)도 같은 기준으로 다시 확인한다 (그사이 누가 같은 이메일로 가입했을 수 있다).
 */
@Service
@RequiredArgsConstructor
public class EmailCheckService {

    private final UserRepository userRepository;
    private final EmailCheckRateLimiter rateLimiter;

    @Transactional(readOnly = true)
    public boolean isAvailable(String email, String ip) {
        rateLimiter.acquire(ip);
        return !userRepository.existsByEmail(User.normalizeEmail(email));
    }
}
