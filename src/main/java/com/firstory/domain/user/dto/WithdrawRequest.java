package com.firstory.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record WithdrawRequest(
        @Schema(description = "비밀번호가 있는 회원은 필수. 카카오로만 가입해 비밀번호가 없으면 보내지 않는다",
                example = "firstory123!") String currentPassword) {

    // 비밀번호가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "WithdrawRequest[***]";
    }
}
