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
    void 이메일_인증으로_비밀번호를_바꾸면_모든_기기가_로그아웃되고_새_비밀번호로만_로그인된다(CapturedOutput output)
            throws Exception {
        String email = newEmail();
        member(email);
        Browser loggedIn = logIn(email, PASSWORD, true);

        Browser browser = browser();
        verifyEmail(browser, email, "PASSWORD_RESET");
        browser.post("/api/auth/password/reset", resetJson(email, NEW_PASSWORD))
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
    void 가입되지_않은_이메일도_응답이_같고_메일은_가지_않는다() throws Exception {
        String registered = newEmail();
        member(registered);
        String unknown = newEmail();

        Browser a = browser();
        Browser b = browser();
        MvcResult sentToRegistered = a.post("/api/auth/email/send-code", sendCodeJson(registered)).andReturn();
        MvcResult sentToUnknown = b.post("/api/auth/email/send-code", sendCodeJson(unknown)).andReturn();

        assertThat(sentToUnknown.getResponse().getStatus()).isEqualTo(200)
                .isEqualTo(sentToRegistered.getResponse().getStatus());
        assertThat(sentToUnknown.getResponse().getContentAsString())
                .isEqualTo(sentToRegistered.getResponse().getContentAsString());
        assertThat(mail.lastText(unknown)).isNull();

        // 인증번호를 모르는 사람에게는 verify 결과도 같다
        String wrongCode = mail.lastCode(registered).equals("000000") ? "111111" : "000000";
        a.post("/api/auth/email/verify", verifyJson(registered, wrongCode))
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
        b.post("/api/auth/email/verify", verifyJson(unknown, wrongCode))
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
    }

    @Test
    void 가입되지_않은_이메일도_발송_횟수_제한이_같다() throws Exception {
        String unknown = newEmail();
        Browser browser = browser();

        browser.post("/api/auth/email/send-code", sendCodeJson(unknown)).andExpect(status().isOk());
        browser.post("/api/auth/email/send-code", sendCodeJson(unknown))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFICATION_RESEND_TOO_SOON"));
    }

    @Test
    void 인증하지_않거나_가입_목적으로_인증했으면_EMAIL_NOT_VERIFIED() throws Exception {
        String email = newEmail();
        member(email);

        browser().post("/api/auth/password/reset", resetJson(email, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        // 가입 목적 인증은 이미 가입된 이메일이라 받을 수 없다. 다른 이메일로 가입 인증을 하고 재설정을 부르면 막힌다
        String other = newEmail();
        Browser signupVerified = browser();
        verifyEmail(signupVerified, other, "SIGNUP");
        signupVerified.post("/api/auth/password/reset", resetJson(other, NEW_PASSWORD))
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void 인증하지_않은_요청에는_비밀번호_규칙_오류를_주지_않는다() throws Exception {
        browser().post("/api/auth/password/reset", resetJson(newEmail(), "short"))
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void 인증_결과는_한_번만_쓴다() throws Exception {
        String email = newEmail();
        member(email);
        Browser browser = browser();
        verifyEmail(browser, email, "PASSWORD_RESET");

        browser.post("/api/auth/password/reset", resetJson(email, NEW_PASSWORD)).andExpect(status().isNoContent());
        browser.post("/api/auth/password/reset", resetJson(email, "another123!"))
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void 인증하고_10분이_지나면_바꿀_수_없다() throws Exception {
        String email = newEmail();
        member(email);
        Browser browser = browser();
        verifyEmail(browser, email, "PASSWORD_RESET");

        advance(Duration.ofMinutes(10));

        browser.post("/api/auth/password/reset", resetJson(email, NEW_PASSWORD))
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void 지금_비밀번호와_같으면_SAME_AS_OLD_PASSWORD이고_인증은_남는다() throws Exception {
        String email = newEmail();
        member(email);
        Browser browser = browser();
        verifyEmail(browser, email, "PASSWORD_RESET");

        browser.post("/api/auth/password/reset", resetJson(email, PASSWORD))
                .andExpect(jsonPath("$.code").value("SAME_AS_OLD_PASSWORD"));
        browser.post("/api/auth/password/reset", resetJson(email, NEW_PASSWORD)).andExpect(status().isNoContent());
    }

    @Test
    void 비밀번호를_바꾸면_로그인_잠금이_풀린다() throws Exception {
        String email = newEmail();
        member(email);
        Browser attacker = browser();
        for (int i = 0; i < 5; i++) {
            attacker.post("/api/auth/login", loginJson(email, "wrong12345"));
        }

        Browser browser = browser();
        verifyEmail(browser, email, "PASSWORD_RESET");
        browser.post("/api/auth/password/reset", resetJson(email, NEW_PASSWORD)).andExpect(status().isNoContent());

        logIn(email, NEW_PASSWORD, false);
    }

    @Test
    void 탈퇴한_회원의_이메일은_가입되지_않은_이메일과_같다() throws Exception {
        String email = newEmail();
        Browser member = signUp(email);
        member.post("/api/me/withdraw", """
                {"currentPassword": "%s"}""".formatted(PASSWORD)).andExpect(status().isNoContent());

        String signupText = mail.lastText(email);

        advance(Duration.ofSeconds(61));
        browser().post("/api/auth/email/send-code", sendCodeJson(email)).andExpect(status().isOk());

        // 가입 때 받은 메일이 그대로 마지막이다. 재설정 메일은 가지 않았다
        assertThat(mail.lastText(email)).isSameAs(signupText);
    }

    @Test
    void 비밀번호가_없는_카카오_회원은_재설정으로_비밀번호를_만들고_이메일로도_로그인한다() throws Exception {
        String code = "reset" + System.nanoTime();
        String email = "kakao-" + code + "@example.com";
        Browser kakaoMember = browser();
        kakaoMember.post("/api/auth/kakao/login", """
                {"code": "%s", "redirectUri": "http://localhost:5173/oauth/kakao"}""".formatted(code))
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"));
        kakaoMember.post("/api/auth/kakao/signup", """
                {"consents": %s, "termsVersion": "2026-10-01"}""".formatted(allConsents()))
                .andExpect(status().isCreated());
        browser().post("/api/auth/login", loginJson(email, NEW_PASSWORD))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        Browser browser = browser();
        verifyEmail(browser, email, "PASSWORD_RESET");
        browser.post("/api/auth/password/reset", resetJson(email, NEW_PASSWORD)).andExpect(status().isNoContent());

        logIn(email, NEW_PASSWORD, false).get("/api/me")
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.kakaoLinked").value(true));
    }

    // 가입 때 같은 이메일로 인증번호를 보냈으니 60초가 지나야 다시 받을 수 있다
    private void member(String email) throws Exception {
        signUp(email);
        advance(Duration.ofSeconds(61));
    }

    private static String sendCodeJson(String email) {
        return """
                {"email": "%s", "purpose": "PASSWORD_RESET"}""".formatted(email);
    }

    private static String verifyJson(String email, String code) {
        return """
                {"email": "%s", "code": "%s"}""".formatted(email, code);
    }

    private static String resetJson(String email, String newPassword) {
        return """
                {"email": "%s", "newPassword": "%s"}""".formatted(email, newPassword);
    }

    private static String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, password);
    }
}
