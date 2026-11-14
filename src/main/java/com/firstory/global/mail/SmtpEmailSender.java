package com.firstory.global.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * firstory.mail.mode=smtp. Gmail SMTP(팀 계정 + 앱 비밀번호)로 보낸다.
 */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * HTML이 있으면 글자와 HTML을 함께(multipart/alternative) 보낸다. 메일 앱이 보여 줄 수 있는 쪽을 고른다.
     */
    @Override
    public void send(String to, MailContent content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, content.html() != null, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(content.subject());
            if (content.html() != null) {
                helper.setText(content.text(), content.html());
            } else {
                helper.setText(content.text());
            }
            mailSender.send(message);
        } catch (MailException | MessagingException e) {
            throw new MailSendException(e);
        }
    }
}
