package com.firstory.domain.child.entity;

import com.firstory.global.entity.AiValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Gender implements AiValue {

    FEMALE("여자아이"),
    MALE("남자아이"),
    UNSPECIFIED("밝히지 않을래요");

    private final String label;
}
