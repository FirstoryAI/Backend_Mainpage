package com.firstory.global.mail;

/**
 * 보낼 메일 한 통.
 *
 * @param text 글자 본문. HTML을 못 여는 메일 앱은 이걸 보여 준다
 * @param html HTML 본문. 없으면 null (글자만 보낸다)
 */
public record MailContent(String subject, String text, String html) {

    // 본문에 인증번호가 있어 로그에 남기지 않는다
    @Override
    public String toString() {
        return "MailContent[subject=" + subject + "]";
    }
}
