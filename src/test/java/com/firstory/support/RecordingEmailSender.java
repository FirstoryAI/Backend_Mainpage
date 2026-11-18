package com.firstory.support;

import com.firstory.domain.user.entity.User;
import com.firstory.global.mail.EmailSender;
import com.firstory.global.mail.MailContent;
import com.firstory.global.mail.LoggingEmailSender;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 통합 테스트용. 보낸 메일을 기억해서 테스트가 인증번호와 알림 문구를 읽을 수 있게 한다. 로그는 LoggingEmailSender에 맡긴다.
 */
public class RecordingEmailSender implements EmailSender {

    private static final Pattern CODE = Pattern.compile("\\d{6}");

    private final LoggingEmailSender delegate = new LoggingEmailSender();
    private final Map<String, Sent> last = new ConcurrentHashMap<>();
    private volatile RuntimeException failure;

    @Override
    public void send(String to, MailContent content) {
        if (failure != null) {
            throw failure;
        }
        last.put(User.normalizeEmail(to), new Sent(content.subject(), content.text(), content.html()));
        delegate.send(to, content);
    }

    /**
     * 다음 발송부터 이 예외를 던진다. null이면 되돌린다.
     */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    /**
     * 이 주소로 마지막에 보낸 메일 본문. 없으면 null.
     */
    public String lastText(String email) {
        return lastSent(email).map(Sent::text).orElse(null);
    }

    /**
     * 이 주소로 마지막에 보낸 메일의 HTML. 없으면 null.
     */
    public String lastHtml(String email) {
        return lastSent(email).map(Sent::html).orElse(null);
    }

    public String lastSubject(String email) {
        return lastSent(email).map(Sent::subject).orElse(null);
    }

    public String lastCode(String email) {
        String text = lastText(email);
        if (text == null) {
            throw new IllegalStateException("이 주소로 보낸 메일이 없어요.");
        }
        Matcher matcher = CODE.matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException("메일에 인증번호가 없어요.");
        }
        return matcher.group();
    }

    private Optional<Sent> lastSent(String email) {
        return Optional.ofNullable(last.get(User.normalizeEmail(email)));
    }

    private record Sent(String subject, String text, String html) {
    }
}
