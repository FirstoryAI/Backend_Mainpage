package com.firstory.story.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SituationTiming {

    PAST("있었던 일"),
    UPCOMING("앞으로 겪을 일");
    // TODO(PM): CRT-01 "알려주고 싶은 주제" 유형 추가 여부 미정

    private final String label;
}
