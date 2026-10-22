package com.firstory.domain.safety.entity;

import java.util.Objects;

/**
 * 위기 신호 검사 결과와 대응.
 *
 * @param category AI가 분류한 값. OK면 null
 * @param reason   부모에게 보여줄 문장. OK면 null
 * @param helpKind 차단일 때 보여줄 도움의 종류. 차단이 아니면 null
 */
public record SafetyOutcome(SafetyAction action, SafetyLevel level, String category, String reason,
                            HelpKind helpKind) {

    public SafetyOutcome {
        Objects.requireNonNull(action);
        Objects.requireNonNull(level);
    }

    public static SafetyOutcome proceed() {
        return new SafetyOutcome(SafetyAction.PROCEED, SafetyLevel.OK, null, null, null);
    }

    /**
     * 입력을 저장하고 다음으로 가도 되는지 (PROCEED, CHOOSE_TONE).
     */
    public boolean canContinue() {
        return action == SafetyAction.PROCEED || action == SafetyAction.CHOOSE_TONE;
    }

    public boolean isBlocked() {
        return action == SafetyAction.BLOCK;
    }
}
