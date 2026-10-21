package com.firstory.global.security;

import com.firstory.domain.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 로그인 사용자. 세션에 직렬화되어 저장된다.
 * getUsername()은 이메일이고, Spring Session 인덱스와 remember-me 토큰이 이 값을 쓴다.
 */
@Getter
public class UserPrincipal implements UserDetails, CredentialsContainer {

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final Long userId;
    private final String email;
    private String passwordHash;

    public UserPrincipal(Long userId, String email, String passwordHash) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    // 인증이 끝나면 비밀번호 해시를 지워 세션에 남지 않게 한다
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public String toString() {
        return "UserPrincipal[userId=" + userId + "]";
    }
}
