package com.firstory.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record VerifyCodeRequest(
        @NotBlank(message = "휴대폰 번호를 입력해 주세요.") @Schema(example = "010-1234-5678") String phone,
        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Schema(description = "6자리 숫자. local 프로필은 항상 000000", example = "000000") String code) {
}
