package com.firstory.domain.auth.controller;

import com.firstory.global.mail.MailSendException;
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

class AuthIntegrationTest extends IntegrationTest {

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 이메일_인증부터_가입_후_내_정보까지_한_번에_된다(CapturedOutput output) throws Exception {
        String email = newEmail();
        Browser browser = browser();
        assertThat(browser.hasCookie(Browser.XSRF)).isTrue();

        browser.post("/api/auth/email/send-code", """
                {"email": "%s", "purpose": "SIGNUP"}""".formatted(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(300));
        assertThat(mail.lastSubject(email)).isEqualTo("[FIRSTORY] 인증번호를 알려 드려요");
        browser.post("/api/auth/email/verify", """
                {"email": "%s", "code": "%s"}""".formatted(email.toUpperCase(), mail.lastCode(email)))
                .andExpect(status().isNoContent());
        browser.post("/api/auth/signup", signupJson(email, allConsents()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.onboardingSeen").value(false))
                .andExpect(jsonPath("$.emailNotify").value(false))
                .andExpect(jsonPath("$.kakaoLinked").value(false))
                .andExpect(jsonPath("$.hasPassword").value(true));

        browser.get("/api/me").andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        // 동의는 가입 = 동의라 따로 저장하지 않고, 알림 수신(선택)만 users.email_notify에 (새 ERD)
        assertThat(jdbc.queryForObject("SELECT email_notify FROM users WHERE email = ?", Boolean.class, email))
                .isFalse();

        // 로그에는 가린 이메일만 남고 비밀번호는 남지 않는다
        assertThat(output.getAll()).doesNotContain("[MAIL] " + email).contains("[MAIL] p").doesNotContain(PASSWORD);
    }

    @Test
    void 이메일_인증_없이_가입하면_EMAIL_NOT_VERIFIED() throws Exception {
        browser().post("/api/auth/signup", signupJson(newEmail(), allConsents()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void 인증한_이메일과_다른_이메일로는_가입할_수_없다() throws Exception {
        Browser browser = browser();
        verifyEmail(browser, newEmail(), "SIGNUP");

        browser.post("/api/auth/signup", signupJson(newEmail(), allConsents()))
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void 인증한_지_10분이_지나면_가입할_수_없다() throws Exception {
        String email = newEmail();
        Browser browser = browser();
        verifyEmail(browser, email, "SIGNUP");

        advance(Duration.ofMinutes(10));

        browser.post("/api/auth/signup", signupJson(email, allConsents()))
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void 필수_동의가_빠지면_REQUIRED_CONSENT_MISSING() throws Exception {
        String email = newEmail();
        Browser browser = browser();
        verifyEmail(browser, email, "SIGNUP");

        browser.post("/api/auth/signup", signupJson(email, """
                        {"GUARDIAN": true, "TERMS": true, "PRIVACY": false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUIRED_CONSENT_MISSING"));
    }

    @Test
    void 이미_가입된_이메일은_인증번호를_보내지_않고_409() throws Exception {
        String email = newEmail();
        signUp(email);

        browser().post("/api/auth/email/send-code", """
                {"email": "%s", "purpose": "SIGNUP"}""".formatted(email.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void 인증한_사이_다른_사람이_같은_이메일로_가입하면_409() throws Exception {
        String email = newEmail();
        Browser late = browser();
        verifyEmail(late, email, "SIGNUP");

        // 다른 기기가 같은 이메일로 먼저 인증하고 가입한다
        advance(Duration.ofSeconds(61));
        signUp(email);

        late.post("/api/auth/signup", signupJson(email, allConsents()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void 메일을_보내지_못하면_503이고_인증도_남지_않는다() throws Exception {
        String email = newEmail();
        Browser browser = browser();
        mail.failWith(new MailSendException(new IllegalStateException("smtp down")));

        browser.post("/api/auth/email/send-code", """
                {"email": "%s", "purpose": "SIGNUP"}""".formatted(email))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_SEND_FAILED"));
        browser.post("/api/auth/email/verify", """
                {"email": "%s", "code": "000000"}""".formatted(email))
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    @Test
    void CSRF_토큰_없이_로그인하면_403() throws Exception {
        String email = newEmail();
        signUp(email);

        browser().postWithoutCsrf("/api/auth/login", """
                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 로그인하지_않고_내_정보를_보면_401() throws Exception {
        browser().get("/api/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 없는_이메일과_틀린_비밀번호는_응답이_완전히_같다() throws Exception {
        String email = newEmail();
        signUp(email);

        MvcResult unknown = browser().post("/api/auth/login", """
                {"email": "%s", "password": "%s"}""".formatted(newEmail(), PASSWORD)).andReturn();
        MvcResult wrong = browser().post("/api/auth/login", """
                {"email": "%s", "password": "wrong12345"}""".formatted(email)).andReturn();

        assertThat(unknown.getResponse().getStatus()).isEqualTo(401).isEqualTo(wrong.getResponse().getStatus());
        assertThat(unknown.getResponse().getContentAsString())
                .contains("INVALID_CREDENTIALS")
                .isEqualTo(wrong.getResponse().getContentAsString());
    }

    @Test
    void 다섯_번_틀리면_맞는_비밀번호도_15분_동안_잠긴다() throws Exception {
        String email = newEmail();
        signUp(email);
        Browser browser = browser();
        String wrong = """
                {"email": "%s", "password": "wrong12345"}""".formatted(email);
        String right = """
                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD);

        for (int i = 0; i < 5; i++) {
            browser.post("/api/auth/login", wrong).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        browser.post("/api/auth/login", right)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        advance(Duration.ofMinutes(15));
        browser.post("/api/auth/login", right).andExpect(status().isOk());
    }

    @Test
    void 로그인_상태_유지를_켜면_FREMEMBER_쿠키와_토큰이_생긴다() throws Exception {
        String email = newEmail();
        signUp(email);

        Browser off = logIn(email, PASSWORD, false);
        assertThat(off.hasCookie(Browser.REMEMBER_ME)).isFalse();
        assertThat(rememberMeTokens(email)).isZero();

        Browser on = logIn(email, PASSWORD, true);
        assertThat(on.cookie(Browser.REMEMBER_ME)).get().satisfies(cookie -> {
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getMaxAge()).isEqualTo((int) Duration.ofDays(90).toSeconds());
        });
        assertThat(rememberMeTokens(email)).isOne();
    }

    @Test
    void 세션_쿠키_없이_FREMEMBER만으로_다시_들어올_수_있다() throws Exception {
        String email = newEmail();
        signUp(email);
        Browser before = logIn(email, PASSWORD, true);

        Browser revisit = new Browser(mockMvc).withCookie(before.cookie(Browser.REMEMBER_ME).orElseThrow());
        assertThat(revisit.hasCookie(Browser.SESSION)).isFalse();

        revisit.get("/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void 로그아웃하면_그_기기만_끊기고_로그인_안_한_상태에서도_204() throws Exception {
        String email = newEmail();
        signUp(email);
        Browser a = logIn(email, PASSWORD, true);
        Browser b = logIn(email, PASSWORD, true);

        a.post("/api/auth/logout", null).andExpect(status().isNoContent());

        assertThat(a.hasCookie(Browser.SESSION)).isFalse();
        assertThat(a.hasCookie(Browser.REMEMBER_ME)).isFalse();
        a.get("/api/me").andExpect(status().isUnauthorized());
        b.get("/api/me").andExpect(status().isOk());
        assertThat(rememberMeTokens(email)).isOne();

        // 로그아웃은 CSRF 토큰도 지운다. 다시 받아서 부른다
        a.fetchCsrf().post("/api/auth/logout", null).andExpect(status().isNoContent());
    }

    @Test
    void 같은_이메일로_60초_안에_다시_받거나_1시간에_6번째_받으면_429() throws Exception {
        String email = newEmail();
        Browser browser = browser();
        String body = """
                {"email": "%s", "purpose": "SIGNUP"}""".formatted(email);

        browser.post("/api/auth/email/send-code", body).andExpect(status().isOk());
        browser.post("/api/auth/email/send-code", body)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFICATION_RESEND_TOO_SOON"));

        for (int i = 2; i <= 5; i++) {
            advance(Duration.ofSeconds(61));
            browser.post("/api/auth/email/send-code", body).andExpect(status().isOk());
        }
        advance(Duration.ofSeconds(61));
        browser.post("/api/auth/email/send-code", body)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFICATION_RATE_LIMITED"));

        // 첫 발송에서 1시간이 지나면 다시 받을 수 있다
        advance(Duration.ofHours(1));
        browser.post("/api/auth/email/send-code", body).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_send_logs WHERE email_hash = encode(sha256(?::bytea), 'hex')",
                Integer.class, email)).isEqualTo(6);
    }

    @Test
    void 인증번호를_5번_틀리면_맞는_번호도_실패한다() throws Exception {
        String email = newEmail();
        Browser browser = browser();
        sendCode(browser, email, "SIGNUP");
        String code = mail.lastCode(email);
        String wrongCode = code.equals("000000") ? "111111" : "000000";
        String wrong = """
                {"email": "%s", "code": "%s"}""".formatted(email, wrongCode);

        for (int i = 0; i < 4; i++) {
            browser.post("/api/auth/email/verify", wrong)
                    .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
        }
        browser.post("/api/auth/email/verify", wrong)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFICATION_ATTEMPTS_EXCEEDED"));

        browser.post("/api/auth/email/verify", """
                {"email": "%s", "code": "%s"}""".formatted(email, code))
                .andExpect(jsonPath("$.code").value("VERIFICATION_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void 인증번호는_5분이_지나면_만료된다() throws Exception {
        String email = newEmail();
        Browser browser = browser();
        sendCode(browser, email, "SIGNUP");

        advance(Duration.ofMinutes(5));

        browser.post("/api/auth/email/verify", """
                {"email": "%s", "code": "%s"}""".formatted(email, mail.lastCode(email)))
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    @Test
    void 입력값이_비면_필드별_오류를_준다() throws Exception {
        browser().post("/api/auth/email/send-code", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.length()").value(2));
    }
}
