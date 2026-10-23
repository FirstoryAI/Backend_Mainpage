package com.firstory.global.config;

import com.firstory.global.ai.AiClient;
import com.firstory.global.ai.FakeAiClient;
import com.firstory.global.ai.HttpAiClient;
import com.firstory.global.storage.FileStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * firstory.ai.mode로 AI 클라이언트를 고른다. http(기본)는 진짜 AI 서버, fake는 서버 없이 고정 응답 (local, test).
 */
@Configuration
public class AiClientConfig {

    @Bean
    @ConditionalOnProperty(name = "firstory.ai.mode", havingValue = "http", matchIfMissing = true)
    public AiClient httpAiClient(FirstoryProperties properties) {
        return HttpAiClient.create(properties.ai());
    }

    @Bean
    @ConditionalOnProperty(name = "firstory.ai.mode", havingValue = "fake")
    public FakeAiClient fakeAiClient(FileStorage fileStorage, FirstoryProperties properties, Clock clock) {
        return new FakeAiClient(fileStorage, properties.story().pageCount(), properties.ai().fake(), clock);
    }
}
