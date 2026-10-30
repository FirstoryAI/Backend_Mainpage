package com.firstory.domain.generation.entity;

import com.firstory.global.entity.AiValue;

/**
 * 그림 위에 글을 얹을 자리. AI가 그림의 이쪽 30%를 비워 둔다.
 */
public enum TextPosition implements AiValue {
    TOP,
    BOTTOM;

    /**
     * AI 값(top | bottom). 값이 없거나 모르는 값이면 BOTTOM (이 필드가 없던 AI 결과와 같다).
     */
    public static TextPosition fromAi(String value) {
        return value != null && value.equalsIgnoreCase(TOP.aiValue()) ? TOP : BOTTOM;
    }
}
