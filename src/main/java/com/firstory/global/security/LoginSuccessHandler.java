package com.firstory.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 로그인 성공 처리. 로그인, 가입 직후 자동 로그인, 비밀번호 변경 뒤 다시 로그인이 함께 쓴다.
 * 로그인을 필터가 아닌 API에서 처리하므로 SecurityContext를 직접 저장한다.
 */
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler {

    private final SecurityContextRepository securityContextRepository;
    private final JsonRememberMeServices rememberMeServices;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SessionAuthenticationStrategy sessionFixation = new ChangeSessionIdAuthenticationStrategy();

    public void login(UserPrincipal principal, boolean rememberMe,
                      HttpServletRequest request, HttpServletResponse response) {
        principal.eraseCredentials();
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());

        // 세션 고정 공격 방지: 이미 세션이 있으면 ID를 바꾼다
        sessionFixation.onAuthentication(authentication, request, response);

        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        if (rememberMe) {
            request.setAttribute(JsonRememberMeServices.REMEMBER_ME_ATTRIBUTE, Boolean.TRUE);
            rememberMeServices.loginSuccess(request, response, authentication);
        }
    }
}
