package com.firstory.domain.generation.entity;

import com.firstory.global.entity.AiValue;

/**
 * 그림 위 글자색. AI가 글 자리의 그림 밝기를 보고 고른다.
 */
public enum TextColor implements AiValue {
    /** 진한 회색 글자 (밝은 그림 위) */
    DARK,
    /** 흰 글자 (어두운 그림 위) */
    LIGHT;

    /**
     * AI 값(dark | light). 없거나 모르는 값이면 null.
     */
    public static TextColor fromAi(String value) {
        if (value == null) {
            return null;
        }
        for (TextColor color : values()) {
            if (color.aiValue().equalsIgnoreCase(value)) {
                return color;
            }
        }
        return null;
    }
}
