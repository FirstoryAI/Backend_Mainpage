package com.firstory.global.sms;

import com.firstory.global.crypto.PhoneNumber;

/**
 * 문자 발송. 실제 대행사 연동은 TASK-07에서 만든다.
 * 운영 프로필에는 아직 구현이 없어서 이 빈을 찾지 못해 기동이 실패한다 (의도된 동작).
 */
public interface SmsSender {

    void send(PhoneNumber to, String text);
}
