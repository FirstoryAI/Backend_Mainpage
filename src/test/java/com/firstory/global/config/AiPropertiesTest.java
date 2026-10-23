package com.firstory.global.config;

import com.firstory.global.config.FirstoryProperties.Ai;
import com.firstory.global.config.FirstoryProperties.Ai.AiMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPropertiesTest {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Test
    void http인데_토큰이_비어_있으면_기동하지_않는다() {
        assertThatThrownBy(() -> new Ai(AiMode.HTTP, "http://localhost:8000", " ", TIMEOUT, TIMEOUT, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Ai(null, "http://localhost:8000", null, TIMEOUT, TIMEOUT, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fake는_토큰이_없어도_된다() {
        assertThat(new Ai(AiMode.FAKE, "http://localhost:8000", null, TIMEOUT, TIMEOUT, null).mode()).isEqualTo(AiMode.FAKE);
    }

    @Test
    void 가짜_AI_설정이_없으면_바로_응답하고_질문_3개() {
        Ai.Fake fake = new Ai(AiMode.FAKE, "http://localhost:8000", null, TIMEOUT, TIMEOUT, null).fake();

        assertThat(fake).isEqualTo(new Ai.Fake(Duration.ZERO, Duration.ZERO, Duration.ZERO, 2));
        assertThat(fake.timedStages()).isFalse();
    }

    @Test
    void 가짜_AI_turns는_0에서_4() {
        assertThatThrownBy(() -> new Ai.Fake(null, null, null, Ai.Fake.MAX_QUESTIONS))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Ai.Fake(null, null, null, -1)).isInstanceOf(IllegalStateException.class);
    }
}
