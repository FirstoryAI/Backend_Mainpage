package com.firstory.global.kakao;

import java.util.Objects;

/**
 * 카카오가 알려 준 회원.
 *
 * @param id            카카오 회원번호
 * @param email         이메일 동의를 받았을 때만. 없으면 null
 * @param emailVerified 카카오가 인증한(그리고 유효한) 이메일인지
 */
public record KakaoUser(String id, String email, boolean emailVerified) {

    public KakaoUser {
        Objects.requireNonNull(id);
        email = email == null || email.isBlank() ? null : email.trim();
        emailVerified = email != null && emailVerified;
    }

    // 이메일이 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "KakaoUser[emailVerified=" + emailVerified + "]";
    }
}
