package com.firstory.domain.auth.service;

import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
