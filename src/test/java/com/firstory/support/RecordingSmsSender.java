package com.firstory.support;

import com.firstory.global.crypto.PhoneNumber;
import com.firstory.global.sms.LoggingSmsSender;
import com.firstory.global.sms.SmsSender;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 통합 테스트용. 보낸 문자를 기억해서 테스트가 인증번호를 읽을 수 있게 한다. 로그는 LoggingSmsSender에 맡긴다.
 */
public class RecordingSmsSender implements SmsSender {

    private static final Pattern CODE = Pattern.compile("\\d{6}");

    private final LoggingSmsSender delegate = new LoggingSmsSender();
    private final Map<String, String> lastText = new ConcurrentHashMap<>();

    @Override
    public void send(PhoneNumber to, String text) {
        lastText.put(to.digits(), text);
        delegate.send(to, text);
    }

    public String lastCode(String phoneDisplay) {
        String text = lastText.get(PhoneNumber.fromDisplay(phoneDisplay).digits());
        if (text == null) {
            throw new IllegalStateException("이 번호로 보낸 문자가 없어요.");
        }
        Matcher matcher = CODE.matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException("문자에 인증번호가 없어요.");
        }
        return matcher.group();
    }
}
