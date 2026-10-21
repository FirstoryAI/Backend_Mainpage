package com.firstory.domain.story.entity;

import com.firstory.global.entity.AiValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// TODO(PM): 화면 문구 확정
@Getter
@RequiredArgsConstructor
public enum SituationCategory implements AiValue {

    FRIEND_CONFLICT("친구와 다툼"),
    NEW_ENVIRONMENT("새로운 환경"),
    SEPARATION("이별"),
    SAFETY("안전"),
    RULES("규칙"),
    FAMILY_CHANGE("가족의 변화"),
    FEAR("두려움"),
    // 고르면 직접 입력(30자)이 필요하다
    OTHER("기타");

    private final String label;
}
