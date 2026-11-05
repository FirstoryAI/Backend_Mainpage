package com.firstory.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record VerifyCodeRequest(
        @NotBlank(message = "이메일을 입력해 주세요.") @Schema(example = "parent@example.com") String email,
        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Schema(description = "6자리 숫자. local, dev 프로필은 항상 000000", example = "000000") String code) {

    @Override
    public String toString() {
        return "VerifyCodeRequest[***]";
    }
}
