package com.firstory.global.sms;

import com.firstory.global.crypto.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬, 테스트용. 문자를 보내지 않고 로그로만 남긴다. 번호는 가리고 내용(인증번호)만 보여준다.
 */
@Slf4j
@Component
@Profile({"local", "test"})
public class LoggingSmsSender implements SmsSender {

    @Override
    public void send(PhoneNumber to, String text) {
        log.info("[SMS] {} ← {}", to.masked(), text);
    }
}
