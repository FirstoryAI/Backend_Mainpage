package com.firstory.domain.generation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 미리보기에서 부모가 옮긴 의성어·의태어 조각 자리 (#100). 보낸 조각만 바꾸고 나머지는 그대로 둔다.
 */
public record SoundWordPositionsRequest(
        @Schema(description = "옮긴 조각. 1~2개")
        @NotEmpty @Size(max = 2) List<@Valid @NotNull Piece> pieces) {

    /**
     * @param index soundWord.pieces 배열 순서 (0부터)
     * @param x     조각 가운데. 그림 전체 폭에 대한 % (0~100)
     * @param y     조각 가운데. 그림 전체 높이에 대한 % (0~100)
     */
    public record Piece(
            @Schema(description = "soundWord.pieces 배열 순서 (0부터)", example = "0") @NotNull @Min(0) Integer index,
            @Schema(description = "조각 가운데. 그림 폭에 대한 % (0~100)", example = "32.5")
            @NotNull @DecimalMin("0") @DecimalMax("100") Double x,
            @Schema(description = "조각 가운데. 그림 높이에 대한 % (0~100)", example = "28.1")
            @NotNull @DecimalMin("0") @DecimalMax("100") Double y) {
    }
}
