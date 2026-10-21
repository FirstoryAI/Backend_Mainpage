package com.firstory.domain.child.entity;

import com.firstory.global.entity.AiValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 좋아하는 것 중 목록에서 고르는 값. 목록에 없는 것은 직접 입력(customInterests)으로 받는다.
 * AI로 보낼 때 직접 입력이 있으면 "other"를 더한다 (AiContextMapper).
 */
// TODO(PM): 화면 문구 확정
@Getter
@RequiredArgsConstructor
public enum InterestCode implements AiValue {

    DINOSAUR("공룡"),
    SPACE("우주"),
    ANIMAL("동물"),
    VEHICLE("탈것"),
    PRINCESS("공주"),
    HERO("히어로"),
    DRAWING("그림 그리기");

    private final String label;
}
