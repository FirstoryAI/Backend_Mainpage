package com.firstory.domain.user.dto;

/**
 * 휴대폰 번호는 가린 형태(010-****-5678)만 내려준다. 전체 번호를 응답하는 API는 없다.
 */
public record MeResponse(Long id, String email, String phoneMasked, boolean onboardingSeen, boolean smsNotify) {
}
