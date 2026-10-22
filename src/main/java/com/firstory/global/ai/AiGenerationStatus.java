package com.firstory.global.ai;

import java.util.Objects;

/**
 * 생성 진행 상태 (스펙 v0.3 7-5).
 *
 * @param currentNode 지금 실행 중인 그래프 노드 (design_story, write_story, illustrate …). 모르면 null
 * @param stage       AI가 계산한 단계 1~4. Spring은 currentNode로 직접 계산한다
 * @param error       FAILED일 때 AI 오류 code. 아니면 null
 */
public record AiGenerationStatus(AiRunState state, Integer runNo, String currentNode, Integer stage, String error) {

    public AiGenerationStatus {
        Objects.requireNonNull(state);
    }
}
