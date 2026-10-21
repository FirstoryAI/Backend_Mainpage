package com.firstory.domain.auth.entity;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void 영문과_숫자를_포함한_8자_이상이면_통과() {
        assertThatCode(() -> PasswordPolicy.validate("firstory123")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("abcdefg1")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("a1" + "x".repeat(62))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"abc1234", "abcdefgh", "12345678", "한글비밀번호입니다"})
    void 짧거나_영문_또는_숫자가_없으면_예외(String password) {
        assertInvalid(password);
    }

    @Test
    void 글자수가_64자를_넘으면_예외() {
        assertInvalid("a1" + "x".repeat(63));
    }

    private static void assertInvalid(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PASSWORD_FORMAT);
    }
}
