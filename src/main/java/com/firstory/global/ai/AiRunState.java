package com.firstory.global.ai;

/**
 * AI 쪽 thread의 실행 상태 (스펙 v0.3 7-5 GET /status의 state).
 */
public enum AiRunState {
    // 인터뷰 질문에서 멈춤
    INTERVIEWING,
    // 생성 전 확인에서 멈춤
    CONFIRMING,
    // 생성 중
    RUNNING,
    // 부모 검토에서 멈춤. result를 읽을 수 있다
    REVIEWING,
    // 확정 후 종료
    COMPLETED,
    // 예외
    FAILED
}
