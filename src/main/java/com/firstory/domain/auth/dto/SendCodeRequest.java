package com.firstory.domain.auth.dto;

import com.firstory.domain.auth.entity.EmailPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendCodeRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식으로 입력해 주세요.")
        @Size(max = 255, message = "이메일은 255자까지 입력할 수 있어요.")
        @Schema(example = "parent@example.com") String email,
        @NotNull(message = "인증 목적을 선택해 주세요.") EmailPurpose purpose) {

    @Override
    public String toString() {
        return "SendCodeRequest[purpose=" + purpose + "]";
    }
}
