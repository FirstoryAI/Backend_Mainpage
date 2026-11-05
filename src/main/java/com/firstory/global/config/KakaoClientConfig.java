package com.firstory.global.config;

import com.firstory.global.kakao.FakeKakaoClient;
import com.firstory.global.kakao.HttpKakaoClient;
import com.firstory.global.kakao.KakaoClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * firstory.kakao.mode로 카카오 로그인 구현을 고른다. http(기본)는 진짜 카카오, fake는 카카오 없이 (local, test).
 */
@Configuration
public class KakaoClientConfig {

    @Bean
    @ConditionalOnProperty(name = "firstory.kakao.mode", havingValue = "http", matchIfMissing = true)
    public KakaoClient httpKakaoClient(FirstoryProperties properties) {
        return HttpKakaoClient.create(properties.kakao());
    }

    @Bean
    @ConditionalOnProperty(name = "firstory.kakao.mode", havingValue = "fake")
    public FakeKakaoClient fakeKakaoClient() {
        return new FakeKakaoClient();
    }
}
