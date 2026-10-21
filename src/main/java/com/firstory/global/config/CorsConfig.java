package com.firstory.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 프론트와 API가 같은 출처면 CORS가 필요 없다. ALLOWED_ORIGINS가 있을 때만 그 출처를 허용한다.
 */
@Configuration
@ConditionalOnExpression("!'${firstory.web.allowed-origins:}'.isBlank()")
public class CorsConfig {

    // Spring MVC의 HandlerMappingIntrospector도 CorsConfigurationSource라서 이름으로 구분한다
    public static final String CORS_SOURCE = "corsConfigurationSource";

    @Bean(CORS_SOURCE)
    public CorsConfigurationSource corsConfigurationSource(FirstoryProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.web().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
