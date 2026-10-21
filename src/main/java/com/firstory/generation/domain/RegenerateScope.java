package com.firstory.generation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 다시 만들기 범위와 AI 파이프라인 시작 노드.
 */
@Getter
@RequiredArgsConstructor
public enum RegenerateScope {

    ALL(null),
    CHARACTER_STORY("design_story"),
    // 글을 다시 쓰면 가이드와 그림도 다시 실행된다
    TEXT("write_story"),
    IMAGES("illustrate_only");

    // AI 서비스 파이프라인 진입점. ALL은 처음부터 실행
    private final String pipelineEntry;
}
