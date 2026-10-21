package com.firstory.domain.child.entity;

import com.firstory.global.config.ClockConfig;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/**
 * 아이 생년월 검증. 형식은 yyyy-MM, 이번 달까지, 만 12세(144개월)까지.
 */
public final class BirthYmPolicy {

    // TODO(PM): 대상 연령 범위 확정. 지금은 0~144개월
    public static final int MAX_AGE_MONTHS = 144;
    private static final Pattern FORMAT = Pattern.compile("^\\d{4}-\\d{2}$");

    private BirthYmPolicy() {
    }

    public static YearMonth parse(String raw, Clock clock) {
        if (raw == null || !FORMAT.matcher(raw.strip()).matches()) {
            throw new BusinessException(ErrorCode.INVALID_BIRTH_YM);
        }
        YearMonth birthYm;
        try {
            birthYm = YearMonth.parse(raw.strip());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_BIRTH_YM);
        }
        validate(birthYm, clock);
        return birthYm;
    }

    public static void validate(YearMonth birthYm, Clock clock) {
        YearMonth thisMonth = YearMonth.from(ClockConfig.today(clock));
        if (birthYm == null
                || birthYm.isAfter(thisMonth)
                || ChronoUnit.MONTHS.between(birthYm, thisMonth) > MAX_AGE_MONTHS) {
            throw new BusinessException(ErrorCode.INVALID_BIRTH_YM);
        }
    }
}
