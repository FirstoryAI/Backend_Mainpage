package com.firstory.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 시간 제한(60초, 3분, 10분, 15분)을 테스트에서 옮길 수 있도록 현재 시각은 항상 이 Clock으로 구한다.
 * 코드에서 Instant.now(), LocalDate.now()를 직접 부르지 않는다.
 */
@Configuration
public class ClockConfig {

    /**
     * 날짜(오늘, 이번 달)를 셀 때 쓰는 서비스 기준 시간대. 시각(Instant)은 UTC 그대로 둔다.
     */
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * 서비스 기준 시간대의 오늘. 1일 00~09시(한국)에 UTC 날짜로 세면 이번 달이 지난달로 잡히는 것을 막는다.
     */
    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), SERVICE_ZONE);
    }
}
