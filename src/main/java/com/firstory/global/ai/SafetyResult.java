package com.firstory.global.ai;

import com.firstory.domain.safety.entity.SafetyLevel;

import java.util.Objects;

/**
 * AI가 판단한 위기 신호.
 *
 * @param category AI가 분류한 값 (예: harmful_discipline). OK면 null
 * @param reason   부모에게 그대로 보여줄 수 있는 문장. OK면 null
 * @param topic    level과 따로 매긴 주제 (예: separation = 이별·죽음). 없으면 null. 막을지는 BlockedTopics가 정한다
 */
public record SafetyResult(SafetyLevel level, String category, String reason, String topic) {

    public SafetyResult {
        Objects.requireNonNull(level);
    }

    public SafetyResult(SafetyLevel level, String category, String reason) {
        this(level, category, reason, null);
    }

    public static SafetyResult ok() {
        return new SafetyResult(SafetyLevel.OK, null, null);
    }
}
