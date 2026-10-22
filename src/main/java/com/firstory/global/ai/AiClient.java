package com.firstory.global.ai;

import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.story.entity.Tone;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 서비스(FastAPI + LangGraph) 호출. 스펙은 docs/FIRSTORY-AI-API-SPEC-v0.3.md.
 * 응답이 30초 넘게 걸릴 수 있으므로 DB 트랜잭션 안에서 부르지 않는다.
 * 실패는 BusinessException(AI_UNAVAILABLE, AI_BUSY, AI_STATE_MISMATCH)으로 던진다.
 */
// 생성 관련 메서드(generate, status, result, review)는 TASK-06에서 추가
public interface AiClient {

    /**
     * @param questionContext 인터뷰 답변이면 직전 질문. 아니면 null
     */
    SafetyResult checkSafety(SafetySource source, List<String> texts, String questionContext);

    /**
     * @param context AiContextMapper.toContext로 만든 StoryInputContext. 아이 호칭은 없다
     * @param tone    부모가 고른 분위기. 고르지 않았으면 null
     */
    InterviewReply startInterview(String threadId, UUID runId, Map<String, Object> context, Tone tone);

    /**
     * @param answer null이면 건너뛰기(skip)
     */
    InterviewReply answerInterview(String threadId, String answer);
}
