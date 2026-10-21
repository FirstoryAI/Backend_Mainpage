package com.firstory.child.domain;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.firstory.support.Fixtures.child;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChildTest {

    @Test
    void 호칭은_앞뒤_공백을_지우고_저장한다() {
        Child child = child(user(), "아이 A");

        child.rename("  우리 첫째  ");

        assertThat(child.getDisplayName()).isEqualTo("우리 첫째");
    }

    @Test
    void 호칭은_30자까지_가능하다() {
        Child child = child(user(), "아이 A");

        child.rename("가".repeat(30));

        assertThat(child.getDisplayName()).hasSize(30);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 호칭이_비었으면_예외(String name) {
        assertInvalidRename(name);
    }

    @Test
    void 호칭이_30자를_넘으면_예외() {
        assertInvalidRename("가".repeat(31));
    }

    private static void assertInvalidRename(String name) {
        Child child = child(user(), "아이 A");

        assertThatThrownBy(() -> child.rename(name))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CHILD_NAME);
        assertThat(child.getDisplayName()).isEqualTo("아이 A");
    }

    @Test
    void 만들_때도_호칭을_검증한다() {
        assertThatThrownBy(() -> child(user(), " "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CHILD_NAME);
    }
}
