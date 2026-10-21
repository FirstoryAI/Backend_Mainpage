package com.firstory.global.crypto;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberTest {

    @Test
    void 하이픈_형식을_받아_숫자로_보관한다() {
        PhoneNumber phone = PhoneNumber.fromDisplay(" 010-1234-5678 ");

        assertThat(phone.digits()).isEqualTo("01012345678");
        assertThat(phone.display()).isEqualTo("010-1234-5678");
        assertThat(PhoneNumber.fromDisplay(phone.display())).isEqualTo(phone);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"01012345678", "011-1234-5678", "010-123-4567", "010-1234-56789", "010 1234 5678"})
    void 형식이_다르면_예외(String input) {
        assertThatThrownBy(() -> PhoneNumber.fromDisplay(input))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PHONE_FORMAT);
    }

    @Test
    void 숫자_생성자도_검증한다() {
        assertThatThrownBy(() -> new PhoneNumber("0111234567"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void toString은_번호를_가린다() {
        assertThat(PhoneNumber.fromDisplay("010-1234-5678").toString()).doesNotContain("1234");
    }
}
