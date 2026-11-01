package com.firstory.domain.auth.controller;

import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetIntegrationTest extends IntegrationTest {

    private static final String NEW_PASSWORD = "newPass123!";

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 문자_인증으로_비밀번호를_바꾸면_모든_기기가_로그아웃되고_새_비밀번호로만_로그인된다(CapturedOutput output)
            throws Exception {
        String email = newEmail();
        String phone = newPhone();
        member(email, phone);
        Browser loggedIn = logIn(email, PASSWORD, true);

        Browser browser = browser();
        verifyPhone(browser, phone, "PASSWORD_RESET");
        browser.post("/api/auth/password/reset", resetJson(phone, NEW_PASSWORD))
                .andExpect(status().isNoContent());

        loggedIn.get("/api/me").andExpect(status().isUnauthorized());
        assertThat(rememberMeTokens(email)).isZero();
        browser.get("/api/me").andExpect(status().isUnauthorized());

        browser().post("/api/auth/login", loginJson(email, PASSWORD))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        logIn(email, NEW_PASSWORD, false);
        assertThat(output.getAll()).doesNotContain(NEW_PASSWORD);
    }

    @Test
    void 가입되지_않은_번호도_응답이_같고_문자는_가지_않는다() throws Exception {
        String registered = newPhone();
        member(newEmail(), registered);
        String unknown = newPhone();

        Browser a = browser();
        Browser b = browser();
        MvcResult sentToRegistered = a.post("/api/auth/phone/send-code", sendCodeJson(registered)).andReturn();
        MvcResult sentToUnknown = b.post("/api/auth/phone/send-code", sendCodeJson(unknown)).andReturn();

        assertThat(sentToUnknown.getResponse().getStatus()).isEqualTo(200)
                .isEqualTo(sentToRegistered.getResponse().getStatus());
        assertThat(sentToUnknown.getResponse().getContentAsString())
                .isEqualTo(sentToRegistered.getResponse().getContentAsString());
        assertThat(sms.lastText(unknown)).isNull();

        // 인증번호를 모르는 사람에게는 verify 결과도 같다
        String wrongCode = sms.lastCode(registered).equals("000000") ? "111111" : "000000";
        a.post("/api/auth/phone/verify", verifyJson(registered, wrongCode))
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
        b.post("/api/auth/phone/verify", verifyJson(unknown, wrongCode))
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
    }

    @Test
    void 가입되지_않은_번호도_발송_횟수_제한이_같다() throws Exception {
        String unknown = newPhone();
        Browser browser = browser();

        browser.post("/api/auth/phone/send-code", sendCodeJson(unknown)).andExpect(status().isOk());
        browser.post("/api/auth/phone/send-code", sendCodeJson(unknown))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SMS_RESEND_TOO_SOON"));
    }

    @Test
    void 인증하지_않거나_다른_목적으로_인증했으면_PHONE_NOT_VERIFIED() throws Exception {
        String phone = newPhone();
        signUp(newEmail(), phone);

        browser().post("/api/auth/password/reset", resetJson(phone, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));

        Browser changePhone = browser();
        advance(Duration.ofSeconds(61));
        verifyPhone(changePhone, phone, "CHANGE_PHONE");
        changePhone.post("/api/auth/password/reset", resetJson(phone, NEW_PASSWORD))
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));
    }

    @Test
    void 인증하지_않은_요청에는_비밀번호_규칙_오류를_주지_않는다() throws Exception {
        browser().post("/api/auth/password/reset", resetJson(newPhone(), "short"))
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));
    }

    @Test
    void 인증_결과는_한_번만_쓴다() throws Exception {
        String phone = newPhone();
        member(newEmail(), phone);
        Browser browser = browser();
        verifyPhone(browser, phone, "PASSWORD_RESET");

        browser.post("/api/auth/password/reset", resetJson(phone, NEW_PASSWORD)).andExpect(status().isNoContent());
        browser.post("/api/auth/password/reset", resetJson(phone, "another123!"))
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));
    }

    @Test
    void 인증하고_10분이_지나면_바꿀_수_없다() throws Exception {
        String phone = newPhone();
        member(newEmail(), phone);
        Browser browser = browser();
        verifyPhone(browser, phone, "PASSWORD_RESET");

        advance(Duration.ofMinutes(10));

        browser.post("/api/auth/password/reset", resetJson(phone, NEW_PASSWORD))
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));
    }

    @Test
    void 지금_비밀번호와_같으면_SAME_AS_OLD_PASSWORD이고_인증은_남는다() throws Exception {
        String phone = newPhone();
        member(newEmail(), phone);
        Browser browser = browser();
        verifyPhone(browser, phone, "PASSWORD_RESET");

        browser.post("/api/auth/password/reset", resetJson(phone, PASSWORD))
                .andExpect(jsonPath("$.code").value("SAME_AS_OLD_PASSWORD"));
        browser.post("/api/auth/password/reset", resetJson(phone, NEW_PASSWORD)).andExpect(status().isNoContent());
    }

    @Test
    void 비밀번호를_바꾸면_로그인_잠금이_풀린다() throws Exception {
        String email = newEmail();
        String phone = newPhone();
        member(email, phone);
        Browser attacker = browser();
        for (int i = 0; i < 5; i++) {
            attacker.post("/api/auth/login", loginJson(email, "wrong12345"));
        }

        Browser browser = browser();
        verifyPhone(browser, phone, "PASSWORD_RESET");
        browser.post("/api/auth/password/reset", resetJson(phone, NEW_PASSWORD)).andExpect(status().isNoContent());

        logIn(email, NEW_PASSWORD, false);
    }

    @Test
    void 탈퇴한_회원의_번호는_가입되지_않은_번호와_같다() throws Exception {
        String phone = newPhone();
        Browser member = signUp(newEmail(), phone);
        member.post("/api/me/withdraw", """
                {"currentPassword": "%s"}""".formatted(PASSWORD)).andExpect(status().isNoContent());

        String signupText = sms.lastText(phone);

        advance(Duration.ofSeconds(61));
        browser().post("/api/auth/phone/send-code", sendCodeJson(phone)).andExpect(status().isOk());

        // 가입 때 받은 문자가 그대로 마지막이다. 재설정 문자는 가지 않았다
        assertThat(sms.lastText(phone)).isSameAs(signupText);
    }

    // 가입 때 같은 번호로 인증번호를 보냈으니 60초가 지나야 다시 받을 수 있다
    private void member(String email, String phone) throws Exception {
        signUp(email, phone);
        advance(Duration.ofSeconds(61));
    }

    private static String sendCodeJson(String phone) {
        return """
                {"phone": "%s", "purpose": "PASSWORD_RESET"}""".formatted(phone);
    }

    private static String verifyJson(String phone, String code) {
        return """
                {"phone": "%s", "code": "%s"}""".formatted(phone, code);
    }

    private static String resetJson(String phone, String newPassword) {
        return """
                {"phone": "%s", "newPassword": "%s"}""".formatted(phone, newPassword);
    }

    private static String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, password);
    }
}
