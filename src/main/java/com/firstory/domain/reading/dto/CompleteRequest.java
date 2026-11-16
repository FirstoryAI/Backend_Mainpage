package com.firstory.domain.reading.dto;

import com.firstory.domain.reading.entity.ChildInterest;
import com.firstory.domain.reading.entity.ConversationFlow;
import com.firstory.domain.reading.entity.ConversationHelpfulness;
import com.firstory.domain.reading.entity.ReadingFeedback;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 함께 읽기 끝 (RD-03). 본문은 없어도 된다. feedback이 있으면 끝내면서 그 세션에 저장한다 (Backend#76).
 */
public record CompleteRequest(@Valid Feedback feedback) {

    /**
     * 읽기 완료 피드백 팝업. 선택 답은 코드나 화면 문구로 보낸다.
     *
     * @param comment 선택. 빈 문자열이면 저장하지 않는다
     */
    public record Feedback(
            @NotNull(message = "아이가 얼마나 흥미를 보였는지 골라 주세요.")
            @Schema(description = "HIGH(매우 흥미로워했어요), MEDIUM(보통이었어요), LOW(흥미를 보이지 않았어요). "
                    + "화면 문구도 받는다", example = "HIGH") ChildInterest childInterest,
            @NotNull(message = "아이와 대화를 나눌 수 있었는지 골라 주세요.")
            @Schema(description = "NATURAL(자연스럽게 대화를 나눴어요), USUAL(평소와 비슷했어요), "
                    + "NONE(대화로 이어지지 않았어요). 화면 문구도 받는다", example = "NATURAL") ConversationFlow conversationFlow,
            @NotNull(message = "대화에 도움이 되었는지 골라 주세요.")
            @Schema(description = "VERY(많은 도움이 되었어요), SOMEWHAT(조금 도움이 되었어요), "
                    + "NOT_MUCH(별로 도움이 되지 않았어요). 화면 문구도 받는다", example = "VERY")
            ConversationHelpfulness conversationHelpfulness,
            @Size(max = ReadingFeedback.MAX_COMMENT_LENGTH, message = "의견은 500자까지 쓸 수 있어요.")
            @Schema(description = "선택. 500자까지, 비워도 된다", example = "") String comment) {

        // 부모 입력이 로그에 남지 않게 한다
        @Override
        public String toString() {
            return "CompleteRequest.Feedback[childInterest=" + childInterest + ", conversationFlow=" + conversationFlow
                    + ", conversationHelpfulness=" + conversationHelpfulness + ", comment=***]";
        }
    }
}
