package com.firstory.domain.safety.entity;

/**
 * 차단(ST-05) 화면에서 보여줄 도움의 종류. 프론트가 이 값에 맞는 상담 기관을 보여준다.
 * 상담 기관 이름과 번호는 백엔드에 두지 않는다 (PM 확정 전, G-FACT).
 */
public enum HelpKind {
    // CRISIS: 아이의 안전
    CHILD_SAFETY,
    // REVISE가 쌓여 차단: 양육 상담
    PARENTING
}
