package com.firstory.domain.generation.dto;

import com.firstory.domain.generation.entity.RegenerateScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 다시 만들기 (REV-02).
 *
 * @param scope    CHARACTER_STORY(이야기·인물), TEXT(글), IMAGES(그림만). ALL은 받지 않는다
 * @param pages    IMAGES일 때 다시 그릴 쪽. 비어 있으면 전체. 다른 범위면 비워야 한다
 * @param feedback 요청 메모 (300자까지). 위기 신호를 먼저 검사한다
 */
public record RegenerateRequest(
        @NotNull(message = "다시 만들 범위를 골라 주세요.")
        @Schema(allowableValues = {"CHARACTER_STORY", "TEXT", "IMAGES"}, example = "IMAGES") RegenerateScope scope,
        @Schema(description = "IMAGES일 때만. 1~8, 비우면 전체", example = "[3]") List<Integer> pages,
        String feedback) {

    // 부모 입력이 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "RegenerateRequest[scope=" + scope + ", pages=" + pages + "]";
    }
}
