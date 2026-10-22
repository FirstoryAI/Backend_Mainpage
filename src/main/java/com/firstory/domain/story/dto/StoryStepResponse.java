package com.firstory.domain.story.dto;

import com.firstory.domain.safety.dto.SafetyResponse;
import com.firstory.domain.safety.entity.SafetyOutcome;
import com.firstory.domain.story.entity.StoryStatus;

/**
 * 동화 만들기 입력 단계의 공통 응답.
 *
 * @param saved        입력을 저장했는지. 다시 쓰기 안내(ASK_REVISE)나 차단(BLOCK)이면 false
 * @param safety       위기 신호 검사 결과. 검사하지 않는 단계(분위기 선택)는 null
 * @param toneRequired 민감 주제가 감지되어 생성 전에 분위기를 골라야 하는지
 */
public record StoryStepResponse(Long storyId, StoryStatus status, boolean saved, SafetyResponse safety,
                                boolean toneRequired) {

    public static StoryStepResponse of(Long storyId, StoryStatus status, boolean saved, SafetyOutcome outcome,
                                       boolean toneRequired) {
        return new StoryStepResponse(storyId, status, saved, SafetyResponse.of(outcome), toneRequired);
    }
}
