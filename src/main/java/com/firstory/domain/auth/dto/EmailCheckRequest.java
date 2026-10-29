package com.firstory.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 형식 검사는 SignupRequest의 email과 같다.
 */
public record EmailCheckRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식으로 입력해 주세요.")
        @Size(max = 255, message = "이메일은 255자까지 입력할 수 있어요.")
        @Schema(example = "parent@example.com")
        String email) {
}
