package com.firstory.domain.safety.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.firstory.support.Fixtures.NOW;
import static com.firstory.support.Fixtures.story;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafetyFlagTest {

    @ParameterizedTest
    @EnumSource(value = SafetyLevel.class, names = {"REVISE", "CRISIS"})
    void REVISE와_CRISIS만_기록한다(SafetyLevel level) {
        SafetyFlag flag = SafetyFlag.record(story(user()), SafetySource.FORM, level, null, "harmful_discipline",
                null, 1, NOW);

        assertThat(flag.getLevel()).isEqualTo(level);
    }

    @ParameterizedTest
    @EnumSource(value = SafetyLevel.class, names = {"OK", "SENSITIVE"})
    void OK와_SENSITIVE는_기록하지_않는다(SafetyLevel level) {
        assertThatThrownBy(() -> SafetyFlag.record(story(user()), SafetySource.FORM, level, null, "none", null, 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 순번은_1부터() {
        assertThatThrownBy(() -> SafetyFlag.record(story(user()), SafetySource.FORM, SafetyLevel.REVISE, null,
                "harmful_discipline", null, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
