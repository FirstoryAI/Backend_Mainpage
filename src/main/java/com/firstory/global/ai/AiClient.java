package com.firstory.global.ai;

import com.firstory.domain.generation.entity.RegenerateScope;
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

    /**
     * 생성 전 확인(CRT-06) 뒤 생성을 시작한다. AI는 바로 받아들이고(202) 백그라운드에서 만든다.
     *
     * @param outputPrefix 결과물 경로 앞부분 (stories/{storyId}/runs/{runNo}/)
     * @param tone         부모가 고른 분위기. 없으면 null
     */
    void generate(String threadId, UUID runId, int runNo, String outputPrefix, Tone tone);

    AiGenerationStatus status(String threadId);

    /**
     * 부모 검토(REVIEWING)에서 멈췄을 때만 결과가 있다.
     */
    AiGenerationResult result(String threadId);

    void approve(String threadId);

    /**
     * @param scope            CHARACTER_STORY, TEXT, IMAGES (ALL은 보내지 않는다)
     * @param pages            IMAGES일 때 다시 그릴 쪽. 비어 있으면 전체
     * @param baseCheckpointId 어느 버전에서 다시 만들지 (그 버전의 checkpointId)
     */
    void regenerate(String threadId, RegenerateScope scope, List<Integer> pages, String feedback,
                    String baseCheckpointId, UUID runId, int runNo, String outputPrefix);

    /**
     * 실패(FAILED)한 실행을 마지막 성공 지점부터 다시 실행한다.
     */
    void retry(String threadId, UUID runId, int runNo, String outputPrefix);
}
