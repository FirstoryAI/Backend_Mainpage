package com.firstory.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("firstory")
public record FirstoryProperties(Crypto crypto, Policy policy, Ai ai) {

    public record Crypto(String phoneAesKey, String phoneHmacKey) {
    }

    // TODO(PM): 다시 만들기 최대 횟수 미정. 임시값 3
    public record Policy(int maxRegenerations) {
    }

    public record Ai(String baseUrl) {
    }
}
