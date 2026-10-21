package com.firstory.global.security;

import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    // 탈퇴한 회원은 @SQLRestriction 때문에 찾아지지 않는다
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(User.normalizeEmail(email))
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없어요."));
    }
}
