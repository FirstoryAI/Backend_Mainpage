package com.firstory.domain.safety.service;

import com.firstory.domain.safety.entity.SafetyAction;
import com.firstory.domain.safety.entity.SafetyLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafetyPolicyTest {

    static final int DEFAULT_LIMIT = 2;

    @Test
    void OK는_그대로_진행() {
        assertThat(SafetyPolicy.decide(SafetyLevel.OK, 0, DEFAULT_LIMIT)).isEqualTo(SafetyAction.PROCEED);
    }

    @Test
    void SENSITIVE는_분위기_선택으로() {
        assertThat(SafetyPolicy.decide(SafetyLevel.SENSITIVE, 0, DEFAULT_LIMIT)).isEqualTo(SafetyAction.CHOOSE_TONE);
    }

    @Test
    void REVISE는_1번째_다시_쓰기_안내_2번째부터_차단() {
        assertThat(SafetyPolicy.decide(SafetyLevel.REVISE, 1, DEFAULT_LIMIT)).isEqualTo(SafetyAction.ASK_REVISE);
        assertThat(SafetyPolicy.decide(SafetyLevel.REVISE, 2, DEFAULT_LIMIT)).isEqualTo(SafetyAction.BLOCK);
        assertThat(SafetyPolicy.decide(SafetyLevel.REVISE, 3, DEFAULT_LIMIT)).isEqualTo(SafetyAction.BLOCK);
    }

    @Test
    void CRISIS는_1번만에_차단() {
        assertThat(SafetyPolicy.decide(SafetyLevel.CRISIS, 0, DEFAULT_LIMIT)).isEqualTo(SafetyAction.BLOCK);
    }

    @Test
    void 한도를_3으로_바꾸면_2번째는_안내_3번째는_차단() {
        assertThat(SafetyPolicy.decide(SafetyLevel.REVISE, 1, 3)).isEqualTo(SafetyAction.ASK_REVISE);
        assertThat(SafetyPolicy.decide(SafetyLevel.REVISE, 2, 3)).isEqualTo(SafetyAction.ASK_REVISE);
        assertThat(SafetyPolicy.decide(SafetyLevel.REVISE, 3, 3)).isEqualTo(SafetyAction.BLOCK);
    }

    @Test
    void REVISE_횟수는_1부터() {
        assertThatThrownBy(() -> SafetyPolicy.decide(SafetyLevel.REVISE, 0, DEFAULT_LIMIT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
