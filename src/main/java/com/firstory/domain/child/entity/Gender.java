package com.firstory.domain.child.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Gender {

    FEMALE("여자아이"),
    MALE("남자아이"),
    UNDISCLOSED("밝히지 않을래요");

    private final String label;
}
