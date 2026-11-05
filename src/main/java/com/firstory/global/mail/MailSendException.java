package com.firstory.global.mail;

/**
 * 메일 서버가 받지 않았다 (연결 실패, 인증 실패 등). 받는 사람 주소와 본문은 메시지에 넣지 않는다.
 */
public class MailSendException extends RuntimeException {

    public MailSendException(Throwable cause) {
        super("메일을 보내지 못했어요: " + cause.getClass().getSimpleName(), cause);
    }
}
