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
    void 영문_숫자_특수문자를_모두_넣은_8자에서_20자면_통과() {
        assertThatCode(() -> PasswordPolicy.validate("firstory123!")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("abcdef1!")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("a1!" + "x".repeat(17))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "_", "+", "-", "=",
            "[", "]", "{", "}", ":", ";", ",", ".", "?", "/", "\\"})
    void 허용한_특수문자는_모두_쓸_수_있다(String special) {
        assertThatCode(() -> PasswordPolicy.validate("abcdef1" + special)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"abcd12!", "abcdefgh", "12345678", "!@#$%^&*", "abcd1234", "abcd!@#$", "1234!@#$"})
    void 짧거나_영문_숫자_특수문자_중_하나라도_없으면_예외(String password) {
        assertInvalid(password);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd123! ", "비밀번호abc12!", "abcd123!~", "abcd123!'", "abcd123!\"", "abcd123!<",
            "abcd123!|", "abcd123!`", "abcd123!\t"})
    void 한글_공백_허용하지_않은_특수문자가_있으면_예외(String password) {
        assertInvalid(password);
    }

    @Test
    void 글자수가_20자를_넘으면_예외() {
        assertInvalid("a1!" + "x".repeat(18));
    }

    private static void assertInvalid(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PASSWORD_FORMAT);
    }
}
