package com.firstory.domain.safety.entity;

/**
 * 위기 신호 대응. 횟수는 동화 단위로 센다(새 동화는 0부터).
 * 판단 자체는 AI 서비스가 맥락을 보고 한다. 죽음·이별 소재 자체는 허용되고,
 * AI가 위험하거나 지나치게 부정적이라고 판단한 경우만 기록한다.
 */
public final class SafetyPolicy {

    private SafetyPolicy() {
    }

    public static SafetyAction decide(int occurrenceNo) {
        if (occurrenceNo < 1) {
            throw new IllegalArgumentException("위기 신호 횟수는 1 이상이어야 해요: " + occurrenceNo);
        }
        return occurrenceNo == 1 ? SafetyAction.WARN_POPUP : SafetyAction.BLOCK;
    }
}
