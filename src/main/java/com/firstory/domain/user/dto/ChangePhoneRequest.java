package com.firstory.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ChangePhoneRequest(
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.") @Schema(example = "firstory123") String currentPassword,
        @NotBlank(message = "휴대폰 번호를 입력해 주세요.") @Schema(example = "010-1234-5678") String phone) {

    // 비밀번호와 번호가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "ChangePhoneRequest[***]";
    }
}
