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
        assertThatThrownBy(() -> new Ai(AiMode.HTTP, "http://localhost:8000", " ", TIMEOUT, TIMEOUT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Ai(null, "http://localhost:8000", null, TIMEOUT, TIMEOUT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fake는_토큰이_없어도_된다() {
        assertThat(new Ai(AiMode.FAKE, "http://localhost:8000", null, TIMEOUT, TIMEOUT).mode()).isEqualTo(AiMode.FAKE);
    }
}
