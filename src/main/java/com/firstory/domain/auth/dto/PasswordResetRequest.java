package com.firstory.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
        @NotBlank(message = "휴대폰 번호를 입력해 주세요.") @Schema(example = "010-1234-5678") String phone,
        @NotBlank(message = "새 비밀번호를 입력해 주세요.") @Schema(example = "firstory456!") String newPassword) {

    // 번호와 비밀번호가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "PasswordResetRequest[***]";
    }
}
