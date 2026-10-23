package com.firstory.domain.auth.dto;

import com.firstory.domain.auth.entity.SmsPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendCodeRequest(
        @NotBlank(message = "휴대폰 번호를 입력해 주세요.") @Schema(example = "010-1234-5678") String phone,
        @NotNull(message = "인증 목적을 선택해 주세요.") SmsPurpose purpose) {
}
