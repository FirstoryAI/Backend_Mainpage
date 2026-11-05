package com.firstory.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MeResponse(
        Long id,
        String email,
        boolean onboardingSeen,
        @Schema(description = "동화 제작 소식을 이메일로 받기 (EMAIL_NOTIFY 동의)") boolean emailNotify,
        @Schema(description = "카카오로 가입한 회원") boolean kakaoLinked,
        @Schema(description = "비밀번호가 있는지. 카카오로만 가입했으면 false (비밀번호 변경 대신 비밀번호 찾기로 만든다)")
        boolean hasPassword) {
}
