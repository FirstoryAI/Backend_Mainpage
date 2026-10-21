package com.firstory.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("firstory")
public record FirstoryProperties(Crypto crypto, Policy policy, Ai ai, Web web, Auth auth) {

    public record Crypto(String phoneAesKey, String phoneHmacKey) {
    }

    // TODO(PM): 다시 만들기 최대 횟수 미정. 임시값 3
    public record Policy(int maxRegenerations) {
    }

    public record Ai(String baseUrl) {
    }

    /**
     * 쿠키와 출처 설정. 도메인은 코드에 적지 않고 환경 변수로만 받는다.
     *
     * @param cookieDomain   비어 있으면 Domain 속성을 쓰지 않는다 (host-only 쿠키)
     * @param allowedOrigins 비어 있으면 CORS 설정을 등록하지 않는다 (같은 출처 배포)
     */
    public record Web(boolean cookieSecure, String cookieDomain, List<String> allowedOrigins) {

        public Web {
            cookieDomain = cookieDomain == null || cookieDomain.isBlank() ? null : cookieDomain.trim();
            allowedOrigins = allowedOrigins == null ? List.of()
                    : allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        }

        public boolean hasCookieDomain() {
            return cookieDomain != null;
        }
    }

    public record Auth(String rememberMeKey) {
    }
}
