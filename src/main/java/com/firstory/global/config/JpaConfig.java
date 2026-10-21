package com.firstory.global.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    /**
     * created_at, updated_at도 Clock 빈을 따른다 (문자 발송 제한이 created_at으로 센다).
     * Clock 빈이 없는 슬라이스 테스트(@DataJpaTest)에서는 시스템 시계를 쓴다.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider(ObjectProvider<Clock> clock) {
        return () -> Optional.of(clock.getIfAvailable(Clock::systemUTC).instant());
    }
}
