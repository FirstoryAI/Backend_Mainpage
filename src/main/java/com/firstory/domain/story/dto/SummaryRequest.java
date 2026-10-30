package com.firstory.domain.story.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 생성 전 확인(CRT-06)에서 고친 "이번 이야기에서 나누고 싶은 것".
 *
 * @param summary 인터뷰가 만든 문장을 부모가 고친 것. 300자까지 (다시 만들기 메모와 같은 길이)
 */
public record SummaryRequest(
        @Schema(example = "속상했던 마음을 먼저 알아주고 싶어요. 친구를 나쁘게 말하지는 않고 싶어요.")
        @NotBlank @Size(max = MAX_LENGTH) String summary) {

    public static final int MAX_LENGTH = 300;

    // 부모 입력이 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "SummaryRequest[***]";
    }
}
