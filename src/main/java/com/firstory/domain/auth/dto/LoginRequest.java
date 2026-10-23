package com.firstory.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * @param rememberMe 로그인 상태 유지. 보내지 않으면 꺼짐
 */
public record LoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요.") @Schema(example = "parent@example.com") String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.") @Schema(example = "firstory123") String password,
        Boolean rememberMe) {

    public boolean rememberMeRequested() {
        return Boolean.TRUE.equals(rememberMe);
    }

    // 비밀번호가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "LoginRequest[rememberMe=" + rememberMe + "]";
    }
}
