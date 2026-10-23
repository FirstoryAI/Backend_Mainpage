package com.firstory.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.") @Schema(example = "firstory123") String currentPassword,
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Schema(description = "8~64자, 영문과 숫자 각각 1개 이상", example = "firstory456") String newPassword) {

    // 비밀번호가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
