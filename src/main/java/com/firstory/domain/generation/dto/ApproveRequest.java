package com.firstory.domain.generation.dto;

import com.firstory.domain.generation.entity.ReadingIntent;
import com.firstory.domain.generation.entity.SituationReflection;
import com.firstory.domain.generation.entity.StoryFeedback;
import com.firstory.domain.generation.entity.StorySatisfaction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 확정 (REV-04). 본문은 없어도 된다. feedback이 있으면 확정과 함께 저장한다 (Backend#76).
 */
public record ApproveRequest(@Valid Feedback feedback) {

    /**
     * 확정 전 피드백 팝업. 선택 답은 코드나 화면 문구로 보낸다.
     *
     * @param comment 선택. 빈 문자열이면 저장하지 않는다
     */
    public record Feedback(
            @NotNull(message = "동화에 상황이 잘 반영되었는지 골라 주세요.")
            @Schema(description = "WELL(잘 반영되었어요), PARTLY(어느 정도 반영되었어요), LACKING(아쉬운 부분이 있어요). "
                    + "화면 문구도 받는다", example = "WELL") SituationReflection situationReflection,
            @NotNull(message = "동화에 만족하는지 골라 주세요.")
            @Schema(description = "SATISFIED(만족해요), NEUTRAL(보통이에요), DISSATISFIED(아쉬워요). 화면 문구도 받는다",
                    example = "SATISFIED") StorySatisfaction storySatisfaction,
            @NotNull(message = "아이와 함께 읽고 싶은지 골라 주세요.")
            @Schema(description = "DEFINITELY(꼭 읽어보고 싶어요), MAYBE(기회가 되면 읽어보고 싶어요), "
                    + "NOT_YET(아직 읽어볼 생각이 없어요). 화면 문구도 받는다", example = "DEFINITELY") ReadingIntent readingIntent,
            @Size(max = StoryFeedback.MAX_COMMENT_LENGTH, message = "의견은 500자까지 쓸 수 있어요.")
            @Schema(description = "선택. 500자까지, 비워도 된다", example = "") String comment) {

        // 부모 입력이 로그에 남지 않게 한다
        @Override
        public String toString() {
            return "ApproveRequest.Feedback[situationReflection=" + situationReflection + ", storySatisfaction="
                    + storySatisfaction + ", readingIntent=" + readingIntent + ", comment=***]";
        }
    }
}
