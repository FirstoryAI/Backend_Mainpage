package com.firstory.global.mail;

/**
 * 메일 발송 (인증번호, 동화 알림). firstory.mail.mode로 구현을 고른다 (MailConfig).
 * 느릴 수 있어 트랜잭션 밖에서 부른다. 보내지 못하면 MailSendException을 던진다.
 */
public interface EmailSender {

    void send(String to, String subject, String text);
}
