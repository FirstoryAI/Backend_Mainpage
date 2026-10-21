package com.firstory.domain.child.entity;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BirthYmPolicyTest {

    // 한국 시각 2026-10-21 09:00
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void yyyy_MM_형식을_받는다() {
        assertThat(BirthYmPolicy.parse("2021-03", CLOCK)).isEqualTo(YearMonth.of(2021, 3));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2021-3", "21-03", "2021-13", "2021-00", "2021/03", "202103"})
    void 형식이_다르면_예외(String raw) {
        assertInvalid(raw);
    }

    @Test
    void 이번_달은_통과하고_다음_달은_예외() {
        assertThat(BirthYmPolicy.parse("2026-10", CLOCK)).isEqualTo(YearMonth.of(2026, 10));
        assertInvalid("2026-11");
    }

    @Test
    void 만_144개월은_통과하고_145개월은_예외() {
        assertThat(BirthYmPolicy.parse("2014-10", CLOCK)).isEqualTo(YearMonth.of(2014, 10));
        assertInvalid("2014-09");
    }

    @Test
    void 이번_달은_한국_시간_기준으로_센다() {
        // UTC로는 10월 31일 15시지만 한국은 이미 11월 1일 00시
        Clock firstOfNovemberKst = Clock.fixed(Instant.parse("2026-10-31T15:00:00Z"), ZoneOffset.UTC);

        assertThat(BirthYmPolicy.parse("2026-11", firstOfNovemberKst)).isEqualTo(YearMonth.of(2026, 11));
    }

    private static void assertInvalid(String raw) {
        assertThatThrownBy(() -> BirthYmPolicy.parse(raw, CLOCK))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BIRTH_YM);
    }
}
