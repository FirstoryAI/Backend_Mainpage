package com.firstory.domain.reading.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.firstory.global.entity.FeedbackChoice;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 읽기 완료 피드백 Q3. FIRSTORY가 아이와의 대화에 실질적으로 도움이 되었나요?
 */
@Getter
@RequiredArgsConstructor
public enum ConversationHelpfulness implements FeedbackChoice {

    VERY("많은 도움이 되었어요"),
    SOMEWHAT("조금 도움이 되었어요"),
    NOT_MUCH("별로 도움이 되지 않았어요");

    private final String label;

    // 코드와 화면 문구를 모두 받는다 (FeedbackChoice)
    @JsonCreator
    public static ConversationHelpfulness from(String value) {
        return FeedbackChoice.of(ConversationHelpfulness.class, value);
    }
}
