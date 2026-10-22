package com.firstory.domain.generation.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * poll-enabled일 때만 스케줄러가 등록되고, poll-interval(예: 5s)을 그대로 읽는지 확인한다.
 * 통합 테스트는 스케줄러를 끄고 돌기 때문에 여기서 따로 본다.
 */
class GenerationPollSchedulerTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(GenerationPoller.class, () -> mock(GenerationPoller.class))
            .withUserConfiguration(GenerationPollScheduler.class);

    @Test
    void poll_enabled면_poll_interval마다_실행한다() {
        runner.withPropertyValues("firstory.generation.poll-enabled=true", "firstory.generation.poll-interval=5s")
                .run(context -> {
                    assertThat(context).hasSingleBean(GenerationPollScheduler.class);
                    assertThat(context.getBean(ScheduledTaskHolder.class).getScheduledTasks())
                            .singleElement()
                            .satisfies(task -> assertThat(((FixedDelayTask) task.getTask()).getIntervalDuration())
                                    .isEqualTo(Duration.ofSeconds(5)));
                });
    }

    @Test
    void poll_enabled가_false면_등록하지_않는다() {
        runner.withPropertyValues("firstory.generation.poll-enabled=false", "firstory.generation.poll-interval=5s")
                .run(context -> assertThat(context).doesNotHaveBean(GenerationPollScheduler.class));
    }
}
