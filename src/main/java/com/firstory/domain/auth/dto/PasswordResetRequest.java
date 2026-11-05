package com.firstory.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
        @NotBlank(message = "이메일을 입력해 주세요.") @Schema(example = "parent@example.com") String email,
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Schema(description = "8~20자, 영문·숫자·특수문자 각각 1개 이상. 특수문자는 !@#$%^&*()_+-=[]{}:;,.?/\\ 만, 한글·공백 불가", example = "firstory456!") String newPassword) {

    // 이메일과 비밀번호가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "PasswordResetRequest[***]";
    }
}
