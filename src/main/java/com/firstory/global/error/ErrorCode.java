package com.firstory.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 다시 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근할 수 없어요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않아요."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "로그인을 여러 번 실패해서 15분 동안 잠겼어요. 잠시 후 다시 시도해 주세요."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "비밀번호는 영문과 숫자를 포함해 8~64자로 입력해 주세요."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 맞지 않아요."),
    SAME_AS_OLD_PASSWORD(HttpStatus.BAD_REQUEST, "지금 쓰는 비밀번호와 다른 비밀번호를 입력해 주세요."),
    SMS_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "인증번호를 너무 많이 요청했어요. 1시간 후 다시 시도해 주세요."),
    SMS_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증번호는 1분 뒤에 다시 받을 수 있어요."),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 맞지 않아요."),
    VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호가 만료됐어요. 다시 받아 주세요."),
    VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증번호를 5번 틀렸어요. 인증번호를 다시 받아 주세요."),
    PHONE_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "휴대폰 인증을 먼저 해 주세요."),
    REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "필수 항목에 모두 동의해야 가입할 수 있어요."),
    INVALID_PHONE_FORMAT(HttpStatus.BAD_REQUEST, "010-0000-0000 형식으로 입력해 주세요."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 가입된 휴대폰 번호예요."),
    INTERESTS_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "좋아하는 것은 고른 것과 직접 입력을 합쳐 5개까지예요."),
    CUSTOM_VALUE_REQUIRED(HttpStatus.BAD_REQUEST, "기타를 고르면 내용을 직접 입력해 주세요."),
    // {n}은 항목별 최대 글자 수로 채운다 (BusinessException(ErrorCode, int))
    CUSTOM_VALUE_TOO_LONG(HttpStatus.BAD_REQUEST, "직접 입력은 {n}자까지 쓸 수 있어요."),
    CHILD_LABEL_EXHAUSTED(HttpStatus.BAD_REQUEST, "저장할 수 있는 아이 수를 넘었어요."),
    INVALID_CHILD_NAME(HttpStatus.BAD_REQUEST, "아이 호칭은 1~30자로 입력해 주세요."),
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "아이 정보를 찾을 수 없어요."),
    INVALID_BIRTH_YM(HttpStatus.BAD_REQUEST, "생년월을 다시 확인해 주세요."),
    STORY_NOT_FOUND(HttpStatus.NOT_FOUND, "동화를 찾을 수 없어요."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 작업이에요."),
    DESCRIPTION_TOO_LONG(HttpStatus.BAD_REQUEST, "상황 설명은 60자까지 쓸 수 있어요."),
    MOMENT_ONLY_FOR_PAST(HttpStatus.BAD_REQUEST, "아이의 반응과 감정은 있었던 일에서만 고를 수 있어요."),
    VERSION_NOT_IN_STORY(HttpStatus.BAD_REQUEST, "이 동화의 버전이 아니에요."),
    REGENERATION_LIMIT_REACHED(HttpStatus.CONFLICT, "다시 만들기 횟수를 모두 썼어요."),
    PAGE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "없는 쪽이에요."),
    STORY_BLOCKED(HttpStatus.CONFLICT, "이 내용으로는 동화를 만들 수 없어요."),
    STORY_INPUT_INCOMPLETE(HttpStatus.BAD_REQUEST, "상황과 아이 정보를 먼저 입력해 주세요."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "동화 도우미가 잠시 응답하지 않아요. 잠시 후 다시 시도해 주세요."),
    AI_BUSY(HttpStatus.CONFLICT, "이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요."),
    AI_STATE_MISMATCH(HttpStatus.CONFLICT, "진행 상태가 맞지 않아요. 화면을 새로고침해 주세요."),
    TONE_REQUIRED(HttpStatus.BAD_REQUEST, "이야기 분위기를 먼저 골라 주세요."),
    INVALID_PAGE_SELECTION(HttpStatus.BAD_REQUEST, "다시 그릴 쪽을 다시 골라 주세요."),
    FEEDBACK_TOO_LONG(HttpStatus.BAD_REQUEST, "요청 메모는 300자까지 쓸 수 있어요."),
    VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "이 동화의 버전을 찾을 수 없어요."),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "그림을 찾을 수 없어요.");

    private final HttpStatus status;
    private final String message;
}
