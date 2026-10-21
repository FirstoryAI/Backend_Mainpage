package com.firstory.domain.story.entity;

import com.firstory.global.entity.AiValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 그 순간 아이의 반응. 여러 개 고를 수 있고, 있었던 일(PAST)에서만 고른다.
 */
// TODO(PM): 화면 문구 확정
@Getter
@RequiredArgsConstructor
public enum MomentReaction implements AiValue {

    CRIED("울었어요"),
    SILENT("말이 없었어요"),
    ANGRY_OUTBURST("화를 냈어요"),
    CLINGY("떨어지지 않으려 했어요"),
    AVOIDED("피했어요"),
    ASKED_QUESTIONS("질문을 했어요"),
    // 고르면 직접 입력(30자)이 필요하다
    OTHER("기타");

    private final String label;
}
