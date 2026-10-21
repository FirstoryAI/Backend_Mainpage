package com.firstory.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시간 제한(60초, 3분, 10분, 15분)을 테스트에서 옮길 수 있도록 현재 시각은 항상 이 Clock으로 구한다.
 * 코드에서 Instant.now()를 직접 부르지 않는다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
