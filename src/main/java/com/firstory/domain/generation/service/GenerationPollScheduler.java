package com.firstory.domain.generation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * firstory.generation.poll-interval마다 생성 진행을 확인한다. poll-enabled=false(test 프로필)면 등록하지 않는다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "firstory.generation.poll-enabled", havingValue = "true")
@RequiredArgsConstructor
public class GenerationPollScheduler {

    private final GenerationPoller poller;

    @Scheduled(fixedDelayString = "${firstory.generation.poll-interval}",
            initialDelayString = "${firstory.generation.poll-interval}")
    void poll() {
        poller.pollOnce();
    }
}
