package com.firstory.domain.reading.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DialogueActionType {

    TALKED("이야기 나눴어요"),
    SKIPPED("건너뛰기");

    private final String label;
}
