package com.firstory.global.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * firstory.mail.mode=log (local, test, dev 프로필만). 메일을 보내지 않고 로그로만 남긴다.
 * 주소는 가리고 내용(인증번호)만 보여준다. 다른 프로필에서 log면 MailConfig가 기동을 막는다.
 */
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String text) {
        log.info("[MAIL] {} ← {} / {}", EmailMask.mask(to), subject, text);
    }
}
