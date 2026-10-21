package com.firstory.safety.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafetyPolicyTest {

    @Test
    void 첫_번째는_경고_팝업() {
        assertThat(SafetyPolicy.decide(1)).isEqualTo(SafetyAction.WARN_POPUP);
    }

    @Test
    void 두_번째부터는_차단() {
        assertThat(SafetyPolicy.decide(2)).isEqualTo(SafetyAction.BLOCK);
        assertThat(SafetyPolicy.decide(3)).isEqualTo(SafetyAction.BLOCK);
    }

    @Test
    void 횟수는_1부터() {
        assertThatThrownBy(() -> SafetyPolicy.decide(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
