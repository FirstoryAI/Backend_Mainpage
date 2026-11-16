package com.firstory.domain.generation.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.firstory.global.entity.FeedbackChoice;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 확정 전 피드백 Q2. 완성된 동화와 이야기에 만족하시나요?
 */
@Getter
@RequiredArgsConstructor
public enum StorySatisfaction implements FeedbackChoice {

    SATISFIED("만족해요"),
    NEUTRAL("보통이에요"),
    DISSATISFIED("아쉬워요");

    private final String label;

    // 코드와 화면 문구를 모두 받는다 (FeedbackChoice)
    @JsonCreator
    public static StorySatisfaction from(String value) {
        return FeedbackChoice.of(StorySatisfaction.class, value);
    }
}
