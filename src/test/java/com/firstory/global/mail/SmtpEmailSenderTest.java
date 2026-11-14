package com.firstory.global.mail;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpEmailSender sender = new SmtpEmailSender(mailSender, "FIRSTORY <team@example.com>");

    @Test
    void HTML이_있으면_글자와_HTML을_같이_보낸다() throws Exception {
        MimeMessage sent = send(new MailContent("[FIRSTORY] 제목", "글자 본문", "<p>HTML 본문</p>"));

        assertThat(sent.getSubject()).isEqualTo("[FIRSTORY] 제목");
        assertThat(sent.getFrom()[0].toString()).contains("team@example.com");
        List<String> types = new ArrayList<>();
        List<Object> bodies = new ArrayList<>();
        collect((Multipart) sent.getContent(), types, bodies);
        assertThat(types).anyMatch(type -> type.startsWith("text/plain")).anyMatch(type -> type.startsWith("text/html"));
        assertThat(bodies).contains("글자 본문", "<p>HTML 본문</p>");
    }

    @Test
    void HTML이_없으면_글자만_보낸다() throws Exception {
        MimeMessage sent = send(new MailContent("[FIRSTORY] 제목", "글자 본문", null));

        assertThat(sent.getContentType()).startsWith("text/plain");
        assertThat(sent.getContent()).isEqualTo("글자 본문");
    }

    private MimeMessage send(MailContent content) throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        sender.send("parent@example.com", content);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        return message;
    }

    private static void collect(Multipart multipart, List<String> types, List<Object> bodies) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            var part = multipart.getBodyPart(i);
            Object content = part.getContent();
            if (content instanceof Multipart nested) {
                collect(nested, types, bodies);
            } else {
                types.add(part.getContentType());
                bodies.add(content);
            }
        }
    }
}
