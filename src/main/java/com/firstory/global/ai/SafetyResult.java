package com.firstory.global.ai;

import com.firstory.domain.safety.entity.SafetyLevel;

import java.util.Objects;

/**
 * AI가 판단한 위기 신호.
 *
 * @param category AI가 분류한 값 (예: harmful_discipline). OK면 null
 * @param reason   부모에게 그대로 보여줄 수 있는 문장. OK면 null
 */
public record SafetyResult(SafetyLevel level, String category, String reason) {

    public SafetyResult {
        Objects.requireNonNull(level);
    }

    public static SafetyResult ok() {
        return new SafetyResult(SafetyLevel.OK, null, null);
    }
}
