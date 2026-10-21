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
    void 온보딩과_문자_알림_동의를_바꿀_수_있다() throws Exception {
        Browser browser = signUp(newEmail(), newPhone());

        browser.post("/api/me/onboarding-seen", null).andExpect(status().isNoContent());
        browser.put("/api/me/consents/sms-notify", """
                {"agreed": true}""").andExpect(status().isNoContent());
        browser.get("/api/me")
                .andExpect(jsonPath("$.onboardingSeen").value(true))
                .andExpect(jsonPath("$.smsNotify").value(true));

        browser.put("/api/me/consents/sms-notify", """
                {"agreed": false}""").andExpect(status().isNoContent());
        browser.get("/api/me").andExpect(jsonPath("$.smsNotify").value(false));
    }

    @Test
    void 비밀번호를_바꾸면_다른_기기는_끊기고_바꾼_기기는_유지된다() throws Exception {
        String email = newEmail();
        Browser a = signUp(email, newPhone());
        Browser b = logIn(email, PASSWORD, true);
        assertThat(rememberMeTokens(email)).isOne();

        a.post("/api/me/password", """
                {"currentPassword": "%s", "newPassword": "newpass123"}""".formatted(PASSWORD))
                .andExpect(status().isNoContent());

        a.get("/api/me").andExpect(status().isOk());
        b.get("/api/me").andExpect(status().isUnauthorized());
        assertThat(rememberMeTokens(email)).isZero();

        logIn(email, "newpass123", false);
    }

    @Test
    void 비밀번호_변경은_현재_비밀번호와_새_비밀번호_규칙을_확인한다() throws Exception {
        Browser browser = signUp(newEmail(), newPhone());

        browser.post("/api/me/password", """
                {"currentPassword": "wrong12345", "newPassword": "newpass123"}""")
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
        browser.post("/api/me/password", """
                {"currentPassword": "%s", "newPassword": "short"}""".formatted(PASSWORD))
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_FORMAT"));
        browser.post("/api/me/password", """
                {"currentPassword": "%s", "newPassword": "%s"}""".formatted(PASSWORD, PASSWORD))
                .andExpect(jsonPath("$.code").value("SAME_AS_OLD_PASSWORD"));
    }

    @Test
    void 휴대폰_번호를_바꿀_수_있다() throws Exception {
        Browser browser = signUp(newEmail(), newPhone());
        String newPhone = newPhone();

        browser.post("/api/me/phone", """
                {"currentPassword": "%s", "phone": "%s"}""".formatted(PASSWORD, newPhone))
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));

        verifyPhone(browser, newPhone, "CHANGE_PHONE");
        browser.post("/api/me/phone", """
                {"currentPassword": "wrong12345", "phone": "%s"}""".formatted(newPhone))
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
        browser.post("/api/me/phone", """
                {"currentPassword": "%s", "phone": "%s"}""".formatted(PASSWORD, newPhone))
                .andExpect(status().isNoContent());

        browser.get("/api/me").andExpect(jsonPath("$.phoneMasked")
                .value(newPhone.substring(0, 4) + "****" + newPhone.substring(8)));
    }

    @Test
    void 가입_인증으로는_번호를_바꿀_수_없다() throws Exception {
        Browser browser = signUp(newEmail(), newPhone());
        String newPhone = newPhone();
        verifyPhone(browser, newPhone, "SIGNUP");

        browser.post("/api/me/phone", """
                {"currentPassword": "%s", "phone": "%s"}""".formatted(PASSWORD, newPhone))
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));
    }

    @Test
    void 탈퇴하면_모든_기기가_끊기고_같은_이메일과_번호로_다시_가입할_수_있다() throws Exception {
        String email = newEmail();
        String phone = newPhone();
        Browser a = signUp(email, phone);
        Browser b = logIn(email, PASSWORD, true);

        a.post("/api/me/withdraw", """
                {"currentPassword": "wrong12345"}""")
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
        a.post("/api/me/withdraw", """
                {"currentPassword": "%s"}""".formatted(PASSWORD))
                .andExpect(status().isNoContent());

        assertThat(a.hasCookie(Browser.SESSION)).isFalse();
        a.get("/api/me").andExpect(status().isUnauthorized());
        b.get("/api/me").andExpect(status().isUnauthorized());
        assertThat(rememberMeTokens(email)).isZero();

        advance(Duration.ofSeconds(61));
        signUp(email, phone).get("/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }
}
