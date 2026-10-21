package com.firstory.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;

/**
 * 로그인 API가 JSON이라 요청 파라미터로 remember-me 여부를 받을 수 없다.
 * 로그인 서비스가 요청 속성(REMEMBER_ME_ATTRIBUTE)에 true를 넣고 loginSuccess를 부르면 토큰을 발급한다.
 */
public class JsonRememberMeServices extends PersistentTokenBasedRememberMeServices {

    public static final String REMEMBER_ME_ATTRIBUTE = JsonRememberMeServices.class.getName() + ".REQUESTED";

    private final RememberMeTokenRepository tokenRepository;

    public JsonRememberMeServices(String key, UserDetailsService userDetailsService,
                                  RememberMeTokenRepository tokenRepository) {
        super(key, userDetailsService, tokenRepository);
        this.tokenRepository = tokenRepository;
    }

    @Override
    protected boolean rememberMeRequested(HttpServletRequest request, String parameter) {
        return Boolean.TRUE.equals(request.getAttribute(REMEMBER_ME_ATTRIBUTE));
    }

    /**
     * 기본 구현은 그 회원의 모든 기기 토큰을 지운다. 로그아웃은 지금 기기의 토큰만 지운다.
     */
    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String cookieValue = extractRememberMeCookie(request);
        if (cookieValue != null) {
            try {
                String[] tokens = decodeCookie(cookieValue);
                if (tokens.length == 2) {
                    tokenRepository.removeSeries(tokens[0]);
                }
            } catch (InvalidCookieException ignored) {
                // 잘못된 쿠키는 지우기만 한다
            }
        }
        cancelCookie(request, response);
    }
}
