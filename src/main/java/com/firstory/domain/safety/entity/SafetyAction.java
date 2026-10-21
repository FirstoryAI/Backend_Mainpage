package com.firstory.domain.safety.entity;

public enum SafetyAction {
    // 그대로 진행
    PROCEED,
    // ST-04 분위기 선택으로
    CHOOSE_TONE,
    // 다시 쓰기 안내
    ASK_REVISE,
    // ST-05 차단
    BLOCK
}
