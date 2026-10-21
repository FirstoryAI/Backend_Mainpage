package com.firstory.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyCodeRequest(
        @NotBlank(message = "휴대폰 번호를 입력해 주세요.") String phone,
        @NotBlank(message = "인증번호를 입력해 주세요.") String code) {
}
