package com.firstory.domain.generation.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.firstory.global.entity.FeedbackChoice;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 확정 전 피드백 Q3. 완성된 동화를 아이와 함께 읽어보고 싶으신가요?
 */
@Getter
@RequiredArgsConstructor
public enum ReadingIntent implements FeedbackChoice {

    DEFINITELY("꼭 읽어보고 싶어요"),
    MAYBE("기회가 되면 읽어보고 싶어요"),
    NOT_YET("아직 읽어볼 생각이 없어요");

    private final String label;

    // 코드와 화면 문구를 모두 받는다 (FeedbackChoice)
    @JsonCreator
    public static ReadingIntent from(String value) {
        return FeedbackChoice.of(ReadingIntent.class, value);
    }
}
