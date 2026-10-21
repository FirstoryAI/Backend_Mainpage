package com.firstory.domain.story.entity;

import com.firstory.global.entity.AiValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 부모가 전하고 싶은 방향. 주로 인터뷰 결과로 정해진다. label은 인터뷰 결과 표시용.
 */
// TODO(PM): 화면 문구 확정
@Getter
@RequiredArgsConstructor
public enum MessageDirection implements AiValue {

    COMFORT_FIRST("먼저 마음을 달래 주고 싶어요"),
    PERSPECTIVE_TAKING("다른 사람의 마음을 생각해 보게 하고 싶어요"),
    EXPLAIN_SITUATION("무슨 일인지 이해하게 돕고 싶어요"),
    SET_BOUNDARY("지켜야 할 약속을 알려 주고 싶어요"),
    REASSURE("괜찮다고 안심시켜 주고 싶어요"),
    UNDECIDED("아직 정하지 않았어요");

    private final String label;
}
