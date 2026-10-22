package com.firstory.domain.story.dto;

import com.firstory.domain.safety.dto.SafetyResponse;
import com.firstory.domain.safety.entity.SafetyOutcome;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.global.ai.InterviewReply;

import java.util.Map;

/**
 * 인터뷰 응답 (CRT-05).
 *
 * <ul>
 *   <li>QUESTION: turn, question이 있다. 다시 쓰기 안내(safety.action ASK_REVISE)면 같은 질문을 다시 준다</li>
 *   <li>CONFIRM: 인터뷰가 끝났다. summary가 있고 status는 CONFIRMING (CRT-06으로)</li>
 *   <li>차단: type, question이 null이고 status는 BLOCKED</li>
 * </ul>
 *
 * @param safety 답변의 위기 신호 검사 결과. 검사하지 않았으면(시작, 건너뛰기) null
 */
public record InterviewResponse(Long storyId, StoryStatus status, InterviewReply.Type type, Integer turn,
                                String question, String summary, Map<String, Object> contextUpdate,
                                SafetyResponse safety, boolean toneRequired) {

    public static InterviewResponse question(Long storyId, StoryStatus status, int turn, String question,
                                             Map<String, Object> contextUpdate, SafetyOutcome outcome,
                                             boolean toneRequired) {
        return new InterviewResponse(storyId, status, InterviewReply.Type.QUESTION, turn, question, null,
                contextUpdate, SafetyResponse.of(outcome), toneRequired);
    }

    public static InterviewResponse confirm(Long storyId, StoryStatus status, String summary, SafetyOutcome outcome,
                                            boolean toneRequired) {
        return new InterviewResponse(storyId, status, InterviewReply.Type.CONFIRM, null, null, summary, null,
                SafetyResponse.of(outcome), toneRequired);
    }

    public static InterviewResponse blocked(Long storyId, SafetyOutcome outcome, boolean toneRequired) {
        return new InterviewResponse(storyId, StoryStatus.BLOCKED, null, null, null, null, null,
                SafetyResponse.of(outcome), toneRequired);
    }

    // 질문과 요약에 부모 입력이 담길 수 있어 로그에 남기지 않는다
    @Override
    public String toString() {
        return "InterviewResponse[storyId=" + storyId + ", status=" + status + ", type=" + type + ", turn=" + turn + "]";
    }
}
