package com.firstory.domain.reading.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.firstory.global.entity.FeedbackChoice;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 읽기 완료 피드백 Q2. 동화를 읽으며 아이와 자연스럽게 대화를 나눌 수 있었나요?
 */
@Getter
@RequiredArgsConstructor
public enum ConversationFlow implements FeedbackChoice {

    NATURAL("자연스럽게 대화를 나눴어요"),
    USUAL("평소와 비슷했어요"),
    NONE("대화로 이어지지 않았어요");

    private final String label;

    // 코드와 화면 문구를 모두 받는다 (FeedbackChoice)
    @JsonCreator
    public static ConversationFlow from(String value) {
        return FeedbackChoice.of(ConversationFlow.class, value);
    }
}
