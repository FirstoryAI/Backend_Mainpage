package com.firstory.domain.reading.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.firstory.global.entity.FeedbackChoice;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 읽기 완료 피드백 Q1. 아이가 동화에 얼마나 흥미를 보였나요?
 */
@Getter
@RequiredArgsConstructor
public enum ChildInterest implements FeedbackChoice {

    HIGH("매우 흥미로워했어요"),
    MEDIUM("보통이었어요"),
    LOW("흥미를 보이지 않았어요");

    private final String label;

    // 코드와 화면 문구를 모두 받는다 (FeedbackChoice)
    @JsonCreator
    public static ChildInterest from(String value) {
        return FeedbackChoice.of(ChildInterest.class, value);
    }
}
