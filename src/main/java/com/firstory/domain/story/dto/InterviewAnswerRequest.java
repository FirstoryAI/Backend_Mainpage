package com.firstory.domain.story.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.firstory.global.entity.CustomText;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * 인터뷰 답변 (CRT-05). answer와 skip 중 하나만 보낸다.
 *
 * @param skip true면 남은 질문을 건너뛰고 인터뷰를 끝낸다
 */
public record InterviewAnswerRequest(
        @Size(max = InterviewAnswerRequest.ANSWER_MAX_LENGTH, message = "답변은 500자까지 쓸 수 있어요.") String answer,
        Boolean skip) {

    public static final int ANSWER_MAX_LENGTH = 500;

    @JsonIgnore
    @AssertTrue(message = "답변을 쓰거나 건너뛰기를 골라 주세요.")
    public boolean isAnswerOrSkip() {
        return (CustomText.strip(answer) != null) != Boolean.TRUE.equals(skip);
    }

    /**
     * @return 앞뒤 공백을 지운 답변. 건너뛰기면 null
     */
    public String answerOrNull() {
        return Boolean.TRUE.equals(skip) ? null : CustomText.strip(answer);
    }

    // 부모 입력이 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "InterviewAnswerRequest[skip=" + skip + "]";
    }
}
