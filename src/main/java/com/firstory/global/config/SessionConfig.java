package com.firstory.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 세션 저장소는 Spring Session JDBC(PostgreSQL)이고 자동 설정을 쓴다. 유휴 만료는 spring.session.timeout(30분).
 * 여기서는 세션 쿠키 속성만 정한다.
 */
@Configuration
public class SessionConfig {

    public static final String SESSION_COOKIE = "FSESSION";

    @Bean
    public CookieSerializer cookieSerializer(FirstoryProperties properties) {
        FirstoryProperties.Web web = properties.web();
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SESSION_COOKIE);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(web.cookieSecure());
        serializer.setSameSite("Lax");
        if (web.hasCookieDomain()) {
            serializer.setDomainName(web.cookieDomain());
        }
        return serializer;
    }
}
