package com.firstory.domain.child.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Trait {

    SENSITIVE("마음이 여려요"),
    SLOW_TO_WARM("낯선 곳에 시간이 걸려요"),
    SHY("수줍음이 많아요"),
    ACTIVE("활발해요"),
    CURIOUS("호기심이 많아요"),
    STUBBORN("고집이 있어요");

    private final String label;
}
