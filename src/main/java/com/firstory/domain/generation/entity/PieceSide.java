package com.firstory.domain.generation.entity;

import com.firstory.global.entity.AiValue;

/**
 * 의성어 조각이 소리 근원의 어느 쪽에 있는지. 프론트가 기울기 방향을 정할 때 쓴다.
 */
public enum PieceSide implements AiValue {
    /** 근원 왼쪽 (두 조각 중 앞) */
    LEFT,
    /** 근원 오른쪽 (두 조각 중 뒤) */
    RIGHT,
    /** 한 조각 */
    CENTER;

    /**
     * AI 값(left | right | center). 없거나 모르는 값이면 null.
     */
    public static PieceSide fromAi(String value) {
        if (value == null) {
            return null;
        }
        for (PieceSide side : values()) {
            if (side.aiValue().equalsIgnoreCase(value)) {
                return side;
            }
        }
        return null;
    }
}
