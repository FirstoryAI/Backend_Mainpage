package com.firstory.domain.safety.entity;

/**
 * AI가 판단한 위기 신호 단계 (AI 응답 값 그대로). 기록(SafetyFlag)은 REVISE, CRISIS만 한다.
 */
public enum SafetyLevel {
    // 문제없음
    OK,
    // 무겁지만 건강하게 다룰 수 있는 주제 (죽음, 이별 등)
    SENSITIVE,
    // 동화 방향이 아이에게 해로움
    REVISE,
    // 아이의 실제 안전이 걱정됨
    CRISIS;

    public boolean isRecorded() {
        return this == REVISE || this == CRISIS;
    }
}
