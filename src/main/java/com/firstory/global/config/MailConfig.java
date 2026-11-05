package com.firstory.global.config;

import com.firstory.global.config.FirstoryProperties.Mail;
import com.firstory.global.config.FirstoryProperties.Mail.MailMode;
import com.firstory.global.mail.EmailSender;
import com.firstory.global.mail.LoggingEmailSender;
import com.firstory.global.mail.SmtpEmailSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * firstory.mail.mode로 메일 발송을 고른다. smtp(기본)는 Gmail 등 SMTP 서버(587, STARTTLS),
 * log는 보내지 않고 로그로만 남긴다. log는 인증번호가 로그에 찍혀서 local, test, dev 프로필에서만 쓸 수 있다.
 */
@Configuration
public class MailConfig {

    static final Profiles LOG_ALLOWED = Profiles.of("local", "test", "dev");

    @Bean
    public EmailSender emailSender(FirstoryProperties properties, Environment environment) {
        Mail mail = properties.mail();
        if (mail.mode() == MailMode.LOG) {
            if (!environment.acceptsProfiles(LOG_ALLOWED)) {
                throw new IllegalStateException(
                        "firstory.mail.mode=log는 local, test, dev 프로필에서만 쓸 수 있어요. MAIL_MODE=smtp로 실행해요.");
            }
            return new LoggingEmailSender();
        }
        return new SmtpEmailSender(javaMailSender(mail), mail.from());
    }

    private static JavaMailSenderImpl javaMailSender(Mail mail) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.host());
        sender.setPort(mail.port());
        sender.setUsername(mail.username());
        sender.setPassword(mail.password());
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
