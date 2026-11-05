package com.firstory.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank(message = "카카오 인가 코드가 없어요.")
        @Schema(description = "카카오 로그인 뒤 redirectUri로 돌아온 code. 한 번만 쓸 수 있다. local은 아무 값(가짜 카카오)",
                example = "kakao-auth-code") String code,
        @NotBlank(message = "redirectUri가 없어요.")
        @Schema(description = "카카오 인가 요청에 쓴 redirect_uri 그대로", example = "http://localhost:5173/oauth/kakao")
        String redirectUri,
        @Schema(description = "이미 가입한 회원이면 로그인 상태 유지(90일). 기본 false") Boolean rememberMe) {

    public boolean rememberMeRequested() {
        return Boolean.TRUE.equals(rememberMe);
    }

    // 인가 코드가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "KakaoLoginRequest[***]";
    }
}
