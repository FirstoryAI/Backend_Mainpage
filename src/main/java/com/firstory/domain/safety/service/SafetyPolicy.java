package com.firstory.domain.safety.service;

import com.firstory.domain.safety.entity.SafetyAction;
import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.global.config.FirstoryProperties;
import org.springframework.stereotype.Component;

/**
 * 위기 신호 대응. 판단(level) 자체는 AI 서비스가 맥락을 보고 한다.
 *
 * <pre>
 * OK         그대로 진행
 * SENSITIVE  분위기 선택(ST-04)으로. 이미 tone을 골랐으면 그대로 진행 (판단은 호출하는 쪽에서)
 * REVISE     limit번째 전까지 다시 쓰기 안내, limit번째부터 차단 (동화 단위, REVISE만 센다)
 * CRISIS     1번만에 차단
 * </pre>
 * REVISE 횟수는 SafetyFlagRepository.countByStoryIdAndLevel로 센다.
 */
@Component
public class SafetyPolicy {

    private final int reviseLimit;

    public SafetyPolicy(FirstoryProperties properties) {
        this.reviseLimit = properties.policy().safetyReviseLimit();
    }

    /**
     * @param reviseCountIncludingThis 이번 것을 포함한 이 동화의 REVISE 횟수. REVISE일 때만 본다
     */
    public SafetyAction decide(SafetyLevel level, int reviseCountIncludingThis) {
        return decide(level, reviseCountIncludingThis, reviseLimit);
    }

    public static SafetyAction decide(SafetyLevel level, int reviseCountIncludingThis, int reviseLimit) {
        return switch (level) {
            case OK -> SafetyAction.PROCEED;
            case SENSITIVE -> SafetyAction.CHOOSE_TONE;
            case REVISE -> {
                if (reviseCountIncludingThis < 1) {
                    throw new IllegalArgumentException("REVISE 횟수는 1 이상이어야 해요: " + reviseCountIncludingThis);
                }
                yield reviseCountIncludingThis < reviseLimit ? SafetyAction.ASK_REVISE : SafetyAction.BLOCK;
            }
            case CRISIS -> SafetyAction.BLOCK;
        };
    }
}
