package com.firstory.domain.user.controller;

import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeIntegrationTest extends IntegrationTest {

    @Test
    void 온보딩과_이메일_알림_동의를_바꿀_수_있다() throws Exception {
        Browser browser = signUp(newEmail());

        browser.post("/api/me/onboarding-seen", null).andExpect(status().isNoContent());
        browser.put("/api/me/consents/email-notify", """
                {"agreed": true}""").andExpect(status().isNoContent());
        browser.get("/api/me")
                .andExpect(jsonPath("$.onboardingSeen").value(true))
                .andExpect(jsonPath("$.emailNotify").value(true));

        browser.put("/api/me/consents/email-notify", """
                {"agreed": false}""").andExpect(status().isNoContent());
        browser.get("/api/me").andExpect(jsonPath("$.emailNotify").value(false));
    }

    @Test
    void 비밀번호를_바꾸면_다른_기기는_끊기고_바꾼_기기는_유지된다() throws Exception {
        String email = newEmail();
        Browser a = signUp(email);
        Browser b = logIn(email, PASSWORD, true);
        assertThat(rememberMeTokens(email)).isOne();

        a.post("/api/me/password", """
                {"currentPassword": "%s", "newPassword": "newpass123!"}""".formatted(PASSWORD))
                .andExpect(status().isNoContent());

        a.get("/api/me").andExpect(status().isOk());
        b.get("/api/me").andExpect(status().isUnauthorized());
        assertThat(rememberMeTokens(email)).isZero();

        logIn(email, "newpass123!", false);
    }

    @Test
    void 비밀번호_변경은_현재_비밀번호와_새_비밀번호_규칙을_확인한다() throws Exception {
        Browser browser = signUp(newEmail());

        browser.post("/api/me/password", """
                {"currentPassword": "wrong12345", "newPassword": "newpass123!"}""")
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
        browser.post("/api/me/password", """
                {"currentPassword": "%s", "newPassword": "short"}""".formatted(PASSWORD))
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_FORMAT"));
        browser.post("/api/me/password", """
                {"currentPassword": "%s", "newPassword": "%s"}""".formatted(PASSWORD, PASSWORD))
                .andExpect(jsonPath("$.code").value("SAME_AS_OLD_PASSWORD"));
    }

    @Test
    void 비밀번호가_없는_카카오_회원은_비밀번호를_바꿀_수_없고_비밀번호_없이_탈퇴한다() throws Exception {
        Browser browser = browser();
        browser.post("/api/auth/kakao/login", """
                {"code": "me%d", "redirectUri": "http://localhost:5173/oauth/kakao"}""".formatted(System.nanoTime()))
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"));
        browser.post("/api/auth/kakao/signup", """
                {"consents": %s, "termsVersion": "2026-10-01"}""".formatted(allConsents()))
                .andExpect(status().isCreated());

        browser.post("/api/me/password", """
                {"currentPassword": "anything1!", "newPassword": "newpass123!"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_NOT_SET"));
        browser.post("/api/me/withdraw", "{}").andExpect(status().isNoContent());
        browser.get("/api/me").andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴하면_모든_기기가_끊기고_같은_이메일로_다시_가입할_수_있다() throws Exception {
        String email = newEmail();
        Browser a = signUp(email);
        Browser b = logIn(email, PASSWORD, true);

        a.post("/api/me/withdraw", """
                {"currentPassword": "wrong12345"}""")
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
        a.post("/api/me/withdraw", "{}").andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
        a.post("/api/me/withdraw", """
                {"currentPassword": "%s"}""".formatted(PASSWORD))
                .andExpect(status().isNoContent());

        assertThat(a.hasCookie(Browser.SESSION)).isFalse();
        a.get("/api/me").andExpect(status().isUnauthorized());
        b.get("/api/me").andExpect(status().isUnauthorized());
        assertThat(rememberMeTokens(email)).isZero();

        advance(Duration.ofSeconds(61));
        signUp(email).get("/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }
}
