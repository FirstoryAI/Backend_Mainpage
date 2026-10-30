package com.firstory.domain.story.dto;

import com.firstory.domain.safety.dto.SafetyResponse;
import com.firstory.domain.safety.entity.SafetyOutcome;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.global.ai.InterviewReply;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * 인터뷰 응답 (CRT-05).
 *
 * <ul>
 *   <li>QUESTION: turn, question, choices가 있다. 다시 쓰기 안내(safety.action ASK_REVISE)면 같은 질문을 다시 주고
 *       choices는 비어 있다 (카드는 저장하지 않는다)</li>
 *   <li>CONFIRM: 인터뷰가 끝났다. summary가 있고 status는 CONFIRMING (CRT-06으로)</li>
 *   <li>차단: type, question이 null이고 status는 BLOCKED</li>
 * </ul>
 *
 * @param safety 답변의 위기 신호 검사 결과. 검사하지 않았으면(시작, 건너뛰기) null
 */
public record InterviewResponse(Long storyId, StoryStatus status, InterviewReply.Type type, Integer turn,
                                String question,
                                @Schema(description = "질문에 부모가 눌러서 답할 수 있는 답변 카드(0~4개). 누르면 그 문장을 답변으로 보낸다. "
                                        + "질문이 아니거나 카드가 없으면 빈 목록",
                                        example = "[\"꼭 안아 줬어요\", \"괜찮다고 말해 줬어요\"]")
                                List<String> choices,
                                String summary,
                                @Schema(description = "AI가 이번 답변까지 정리한 내용. 형식이 정해져 있지 않아 화면에 쓰지 않는다",
                                        example = "{\"answered_turns\": 1}")
                                Map<String, Object> contextUpdate,
                                SafetyResponse safety, boolean toneRequired) {

    public static InterviewResponse question(Long storyId, StoryStatus status, int turn, String question,
                                             List<String> choices, Map<String, Object> contextUpdate,
                                             SafetyOutcome outcome, boolean toneRequired) {
        return new InterviewResponse(storyId, status, InterviewReply.Type.QUESTION, turn, question,
                List.copyOf(choices), null, contextUpdate, SafetyResponse.of(outcome), toneRequired);
    }

    public static InterviewResponse confirm(Long storyId, StoryStatus status, String summary, SafetyOutcome outcome,
                                            boolean toneRequired) {
        return new InterviewResponse(storyId, status, InterviewReply.Type.CONFIRM, null, null, List.of(), summary,
                null, SafetyResponse.of(outcome), toneRequired);
    }

    public static InterviewResponse blocked(Long storyId, SafetyOutcome outcome, boolean toneRequired) {
        return new InterviewResponse(storyId, StoryStatus.BLOCKED, null, null, null, List.of(), null, null,
                SafetyResponse.of(outcome), toneRequired);
    }

    // 질문과 요약에 부모 입력이 담길 수 있어 로그에 남기지 않는다
    @Override
    public String toString() {
        return "InterviewResponse[storyId=" + storyId + ", status=" + status + ", type=" + type + ", turn=" + turn + "]";
    }
}
