package com.firstory.domain.auth.dto;

import com.firstory.domain.user.entity.ConsentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 비밀번호 형식은 PasswordPolicy가, 필수 동의는 ConsentPolicy가 따로 검사한다.
 */
public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식으로 입력해 주세요.")
        @Size(max = 255, message = "이메일은 255자까지 입력할 수 있어요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password,

        @NotBlank(message = "휴대폰 번호를 입력해 주세요.")
        String phone,

        Map<ConsentType, Boolean> consents,

        @NotBlank(message = "약관 버전이 없어요.")
        @Size(max = 20, message = "약관 버전은 20자까지예요.")
        String termsVersion) {

    // 비밀번호가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "SignupRequest[consents=" + consents + ", termsVersion=" + termsVersion + "]";
    }
}
