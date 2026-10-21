package com.firstory.domain.user.entity;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsentPolicyTest {

    @Test
    void 필수_3개에_동의하면_통과() {
        Map<ConsentType, Boolean> agreements = allRequired();

        assertThatCode(() -> ConsentPolicy.validateRequired(agreements)).doesNotThrowAnyException();
    }

    @Test
    void 선택_항목은_동의하지_않아도_통과() {
        Map<ConsentType, Boolean> agreements = allRequired();
        agreements.put(ConsentType.SMS_NOTIFY, false);

        assertThatCode(() -> ConsentPolicy.validateRequired(agreements)).doesNotThrowAnyException();
    }

    @Test
    void 필수_항목이_false면_예외() {
        Map<ConsentType, Boolean> agreements = allRequired();
        agreements.put(ConsentType.PRIVACY, false);

        assertMissing(agreements);
    }

    @Test
    void 필수_항목이_빠지면_예외() {
        Map<ConsentType, Boolean> agreements = allRequired();
        agreements.remove(ConsentType.GUARDIAN);

        assertMissing(agreements);
        assertMissing(null);
    }

    private static Map<ConsentType, Boolean> allRequired() {
        Map<ConsentType, Boolean> agreements = new EnumMap<>(ConsentType.class);
        agreements.put(ConsentType.GUARDIAN, true);
        agreements.put(ConsentType.TERMS, true);
        agreements.put(ConsentType.PRIVACY, true);
        return agreements;
    }

    private static void assertMissing(Map<ConsentType, Boolean> agreements) {
        assertThatThrownBy(() -> ConsentPolicy.validateRequired(agreements))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REQUIRED_CONSENT_MISSING);
    }
}
