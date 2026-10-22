package com.firstory.domain.safety.dto;

import com.firstory.domain.safety.entity.HelpKind;
import com.firstory.domain.safety.entity.SafetyAction;
import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetyOutcome;

/**
 * 위기 신호 검사 결과. 프론트는 action에 따라 다시 쓰기 안내(reason), 분위기 선택(ST-04), 차단(ST-05)을 보여준다.
 *
 * @param helpKind 차단일 때 보여줄 상담 기관 종류. 기관 이름과 번호는 프론트가 정한다
 */
public record SafetyResponse(SafetyAction action, SafetyLevel level, String category, String reason,
                             HelpKind helpKind) {

    public static SafetyResponse of(SafetyOutcome outcome) {
        if (outcome == null) {
            return null;
        }
        return new SafetyResponse(outcome.action(), outcome.level(), outcome.category(), outcome.reason(),
                outcome.helpKind());
    }
}
