package com.firstory.domain.auth.controller;

import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailCheckIntegrationTest extends IntegrationTest {

    private static final String URL = "/api/auth/email/check";

    @Test
    void 가입되지_않은_이메일은_available_true_가입된_이메일은_false() throws Exception {
        String email = newEmail();
        Browser browser = browserFrom(newIp());

        browser.post(URL, body(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        signUp(email, newPhone());

        browser.post(URL, body(email)).andExpect(jsonPath("$.available").value(false));
        // 가입과 같이 대소문자는 무시한다
        browser.post(URL, body(email.toUpperCase(Locale.ROOT)))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void 탈퇴한_회원의_이메일은_다시_가입할_수_있어_available_true() throws Exception {
        String email = newEmail();
        Browser member = signUp(email, newPhone());
        member.post("/api/me/withdraw", """
                {"currentPassword": "%s"}""".formatted(PASSWORD)).andExpect(status().isNoContent());

        browserFrom(newIp()).post(URL, body(email)).andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 형식이_틀린_이메일은_VALIDATION_FAILED() throws Exception {
        Browser browser = browserFrom(newIp());

        browser.post(URL, body("not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0].field").value("email"));
        browser.post(URL, "{}").andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 같은_IP에서_1분에_11번째면_429_다른_IP는_그대로_1분_지나면_다시_된다() throws Exception {
        String ip = newIp();
        Browser browser = browserFrom(ip);
        for (int i = 1; i <= 10; i++) {
            browser.post(URL, body(newEmail())).andExpect(status().isOk());
        }

        browser.post(URL, body(newEmail()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_CHECK_RATE_LIMITED"));
        // 세션을 버려도(새 브라우저) 같은 IP면 막힌다
        browserFrom(ip).post(URL, body(newEmail())).andExpect(status().isTooManyRequests());
        browserFrom(newIp()).post(URL, body(newEmail())).andExpect(status().isOk());

        advance(Duration.ofMinutes(1));
        browser.post(URL, body(newEmail())).andExpect(status().isOk());
    }

    @Test
    void CSRF_토큰이_없으면_403() throws Exception {
        browserFrom(newIp()).postWithoutCsrf(URL, body(newEmail())).andExpect(status().isForbidden());
    }

    // 앞단 프록시(Railway)가 넣는 X-Forwarded-For를 IP로 쓴다 (forward-headers-strategy: framework)
    private Browser browserFrom(String ip) throws Exception {
        return browser().withHeader("X-Forwarded-For", ip);
    }

    private static String newIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "10.%d.%d.%d".formatted(random.nextInt(256), random.nextInt(256), random.nextInt(1, 255));
    }

    private static String body(String email) {
        return """
                {"email": "%s"}""".formatted(email);
    }
}
