package com.firstory.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConsentType {

    GUARDIAN(true, "아이의 보호자(법정대리인)예요"),
    TERMS(true, "서비스 이용약관"),
    PRIVACY(true, "아이 정보를 포함한 개인정보 수집·이용"),
    SMS_NOTIFY(false, "동화 제작 소식을 문자로 받기");

    private final boolean required;
    private final String label;
}
