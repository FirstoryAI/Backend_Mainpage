package com.firstory.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "필수 항목에 모두 동의해야 가입할 수 있어요."),
    INVALID_PHONE_FORMAT(HttpStatus.BAD_REQUEST, "010-0000-0000 형식으로 입력해 주세요."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 가입된 휴대폰 번호예요."),
    INTERESTS_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "좋아하는 것은 5개까지 입력할 수 있어요."),
    INTEREST_TOO_LONG(HttpStatus.BAD_REQUEST, "좋아하는 것은 한 개당 20자까지 입력할 수 있어요."),
    CHILD_LABEL_EXHAUSTED(HttpStatus.BAD_REQUEST, "저장할 수 있는 아이 수를 넘었어요."),
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "아이 정보를 찾을 수 없어요."),
    STORY_NOT_FOUND(HttpStatus.NOT_FOUND, "동화를 찾을 수 없어요."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 작업이에요."),
    EMOTION_ONLY_FOR_PAST(HttpStatus.BAD_REQUEST, "감정은 있었던 일에서만 입력할 수 있어요."),
    VERSION_NOT_IN_STORY(HttpStatus.BAD_REQUEST, "이 동화의 버전이 아니에요."),
    REGENERATION_LIMIT_REACHED(HttpStatus.CONFLICT, "다시 만들기 횟수를 모두 썼어요."),
    PAGE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "없는 쪽이에요.");

    private final HttpStatus status;
    private final String message;
}
