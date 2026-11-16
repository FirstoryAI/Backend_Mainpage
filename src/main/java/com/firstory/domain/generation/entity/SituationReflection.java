package com.firstory.domain.generation.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.firstory.global.entity.FeedbackChoice;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 확정 전 피드백 Q1. 입력한 아이의 상황이 동화에 잘 반영되었나요?
 */
@Getter
@RequiredArgsConstructor
public enum SituationReflection implements FeedbackChoice {

    WELL("잘 반영되었어요"),
    PARTLY("어느 정도 반영되었어요"),
    LACKING("아쉬운 부분이 있어요");

    private final String label;

    // 코드와 화면 문구를 모두 받는다 (FeedbackChoice)
    @JsonCreator
    public static SituationReflection from(String value) {
        return FeedbackChoice.of(SituationReflection.class, value);
    }
}
