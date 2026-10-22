package com.firstory.domain.story.dto;

import com.firstory.domain.story.entity.Emotion;
import com.firstory.domain.story.entity.MomentReaction;
import com.firstory.domain.story.entity.SituationCategory;
import com.firstory.domain.story.entity.SituationTiming;
import jakarta.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * 상황과 그 순간 아이의 반응, 감정 (CRT-01, CRT-02).
 * 기타(OTHER)를 고르면 직접 입력이 필수이고, 고르지 않았으면 직접 입력은 버린다. 반응과 감정은 있었던 일(PAST)에서만.
 */
public record SituationRequest(
        @NotNull(message = "있었던 일인지 앞으로 겪을 일인지 골라 주세요.") SituationTiming timing,
        @NotNull(message = "상황을 골라 주세요.") SituationCategory category,
        String customCategory,
        String description,
        List<MomentReaction> reactions,
        String customReaction,
        List<Emotion> emotions) {

    /**
     * 위기 신호를 검사할 글. 버려질 직접 입력은 넣지 않는다.
     */
    public List<String> textsToCheck() {
        return Arrays.asList(description,
                category == SituationCategory.OTHER ? customCategory : null,
                reactions != null && reactions.contains(MomentReaction.OTHER) ? customReaction : null);
    }

    // 부모 입력이 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "SituationRequest[***]";
    }
}
