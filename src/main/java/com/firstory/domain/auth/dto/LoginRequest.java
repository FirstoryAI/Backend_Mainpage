package com.firstory.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요.") @Schema(example = "parent@example.com") String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.") @Schema(example = "firstory123!") String password,
        Boolean rememberMe) {

    public boolean rememberMeRequested() {
        return Boolean.TRUE.equals(rememberMe);
    }
    @Override
    public String toString() {
        return "LoginRequest[rememberMe=" + rememberMe + "]";
    }
}
