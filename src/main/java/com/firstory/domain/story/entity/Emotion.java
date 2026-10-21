package com.firstory.domain.story.entity;

import com.firstory.global.entity.AiValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 아이가 느낀 것 같은 감정. 여러 개 고를 수 있고, 있었던 일(PAST)에서만 고른다.
 */
// TODO(PM): 화면 문구 확정
@Getter
@RequiredArgsConstructor
public enum Emotion implements AiValue {

    SAD("슬퍼요"),
    ANGRY("화나요"),
    SCARED("무서워요"),
    CONFUSED("헷갈려요"),
    ASHAMED("부끄러워요"),
    EXCITED("설레요"),
    UNKNOWN("잘 모르겠어요");

    private final String label;
}
