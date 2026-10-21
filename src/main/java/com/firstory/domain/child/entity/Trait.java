package com.firstory.domain.child.entity;

import com.firstory.global.entity.AiValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 선언 순서가 응답 정렬 순서다.
 */
@Getter
@RequiredArgsConstructor
public enum Trait implements AiValue {

    SENSITIVE("마음이 여려요"),
    CAUTIOUS("낯선 곳에 시간이 걸려요"),
    SHY("수줍음이 많아요"),
    ACTIVE("활발해요"),
    CURIOUS("호기심이 많아요"),
    STUBBORN("고집이 있어요"),
    // 고르면 Child.customTrait를 직접 입력해야 한다
    OTHER("기타");

    private final String label;
}
