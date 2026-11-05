package com.firstory.domain.auth.dto;

import com.firstory.domain.user.entity.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;


public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식으로 입력해 주세요.")
        @Size(max = 255, message = "이메일은 255자까지 입력할 수 있어요.")
        @Schema(description = "같은 세션에서 purpose SIGNUP으로 인증을 마친 이메일", example = "parent@example.com")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Schema(description = "8~20자, 영문·숫자·특수문자 각각 1개 이상. 특수문자는 !@#$%^&*()_+-=[]{}:;,.?/\\ 만, 한글·공백 불가", example = "firstory123!")
        String password,

        @Schema(description = "동의 항목별 동의 여부. GUARDIAN, TERMS, PRIVACY는 필수(true), EMAIL_NOTIFY는 선택. "
                + "다른 키는 VALIDATION_FAILED",
                example = "{\"GUARDIAN\": true, \"TERMS\": true, \"PRIVACY\": true, \"EMAIL_NOTIFY\": false}")
        Map<ConsentType, Boolean> consents,

        @NotBlank(message = "약관 버전이 없어요.")
        @Size(max = 20, message = "약관 버전은 20자까지예요.")
        @Schema(example = "2026-10-01")
        String termsVersion) {

    @Override
    public String toString() {
        return "SignupRequest[consents=" + consents + ", termsVersion=" + termsVersion + "]";
    }
}
