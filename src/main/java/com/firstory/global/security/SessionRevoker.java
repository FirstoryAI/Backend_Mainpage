package com.firstory.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * 한 회원의 모든 로그인을 끊는다. 비밀번호 변경, 탈퇴 때 쓴다.
 * Spring Session의 principal 인덱스와 remember-me 토큰은 모두 이메일을 키로 쓴다.
 */
@Component
@RequiredArgsConstructor
public class SessionRevoker {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final RememberMeTokenRepository rememberMeTokenRepository;

    public void revokeAll(String email) {
        sessionRepository.findByPrincipalName(email).keySet().forEach(sessionRepository::deleteById);
        rememberMeTokenRepository.removeUserTokens(email);
    }
}
