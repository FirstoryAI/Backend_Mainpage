package com.firstory.domain.auth.dto;

import com.firstory.domain.user.dto.MeResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param status LOGGED_IN이면 me가 있고 로그인됐다. SIGNUP_REQUIRED면 가입 화면(동의)으로 가고 email, emailVerified를 쓴다
 */
public record KakaoLoginResponse(
        Status status,
        @Schema(description = "LOGGED_IN일 때만") MeResponse me,
        @Schema(description = "SIGNUP_REQUIRED일 때 카카오가 준 이메일. 동의하지 않았으면 null") String email,
        @Schema(description = "SIGNUP_REQUIRED일 때 카카오가 인증한 이메일인지. false면 가입 전에 이메일 인증(SIGNUP)이 필요하다")
        Boolean emailVerified) {

    public enum Status {
        LOGGED_IN,
        SIGNUP_REQUIRED
    }

    public static KakaoLoginResponse loggedIn(MeResponse me) {
        return new KakaoLoginResponse(Status.LOGGED_IN, me, null, null);
    }

    public static KakaoLoginResponse signupRequired(String email, boolean emailVerified) {
        return new KakaoLoginResponse(Status.SIGNUP_REQUIRED, null, email, emailVerified);
    }

    @Override
    public String toString() {
        return "KakaoLoginResponse[status=" + status + "]";
    }
}
