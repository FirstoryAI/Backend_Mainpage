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

class AuthIntegrationTest extends IntegrationTest {

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 문자_인증부터_가입_후_내_정보까지_한_번에_된다(CapturedOutput output) throws Exception {
        String email = newEmail();
        String phone = newPhone();
        Browser browser = browser();
        assertThat(browser.hasCookie(Browser.XSRF)).isTrue();

        browser.post("/api/auth/phone/send-code", """
                {"phone": "%s", "purpose": "SIGNUP"}""".formatted(phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(180));
        browser.post("/api/auth/phone/verify", """
                {"phone": "%s", "code": "%s"}""".formatted(phone, sms.lastCode(phone)))
                .andExpect(status().isNoContent());
        browser.post("/api/auth/signup", signupJson(email, phone, allConsents()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phoneMasked").value(masked(phone)))
                .andExpect(jsonPath("$.onboardingSeen").value(false))
                .andExpect(jsonPath("$.smsNotify").value(false));

        MvcResult me = browser.get("/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneMasked").value(masked(phone)))
                .andReturn();

        // 응답과 로그 어디에도 전체 번호가 없다. 로그에는 가린 번호만 남는다
        assertThat(me.getResponse().getContentAsString()).doesNotContain(phone).doesNotContain(digits(phone));
        assertThat(output.getAll()).doesNotContain(phone).doesNotContain(digits(phone)).contains(masked(phone));
        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }

    @Test
    void 휴대폰_인증_없이_가입하면_PHONE_NOT_VERIFIED() throws Exception {
        browser().post("/api/auth/signup", signupJson(newEmail(), newPhone(), allConsents()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));
    }

    @Test
    void 인증한_지_10분이_지나면_가입할_수_없다() throws Exception {
        String phone = newPhone();
        Browser browser = browser();
        verifyPhone(browser, phone, "SIGNUP");

        advance(Duration.ofMinutes(10));

        browser.post("/api/auth/signup", signupJson(newEmail(), phone, allConsents()))
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));
    }

    @Test
    void 필수_동의가_빠지면_REQUIRED_CONSENT_MISSING() throws Exception {
        String phone = newPhone();
        Browser browser = browser();
        verifyPhone(browser, phone, "SIGNUP");

        browser.post("/api/auth/signup", signupJson(newEmail(), phone, """
                        {"GUARDIAN": true, "TERMS": true, "PRIVACY": false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUIRED_CONSENT_MISSING"));
    }

    @Test
    void 이미_가입된_이메일은_409() throws Exception {
        String email = newEmail();
        signUp(email, newPhone());

        String otherPhone = newPhone();
        Browser browser = browser();
        verifyPhone(browser, otherPhone, "SIGNUP");
        browser.post("/api/auth/signup", signupJson(email.toUpperCase(), otherPhone, allConsents()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void 이미_가입된_번호는_409() throws Exception {
        String phone = newPhone();
        signUp(newEmail(), phone);

        browser().post("/api/auth/phone/send-code", """
                {"phone": "%s", "purpose": "SIGNUP"}""".formatted(phone))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PHONE"));
    }

    @Test
    void 인증한_사이_다른_사람이_같은_번호로_가입하면_409() throws Exception {
        String phone = newPhone();
        Browser late = browser();
        verifyPhone(late, phone, "SIGNUP");

        // 다른 기기가 같은 번호로 먼저 인증하고 가입한다
        advance(Duration.ofSeconds(61));
        signUp(newEmail(), phone);

        late.post("/api/auth/signup", signupJson(newEmail(), phone, allConsents()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PHONE"));
    }

    @Test
    void CSRF_토큰_없이_로그인하면_403() throws Exception {
        String email = newEmail();
        signUp(email, newPhone());

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
        signUp(email, newPhone());

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
        signUp(email, newPhone());
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
        signUp(email, newPhone());

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
        signUp(email, newPhone());
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
        signUp(email, newPhone());
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
    void 같은_번호로_60초_안에_다시_받거나_1시간에_6번째_받으면_429() throws Exception {
        String phone = newPhone();
        Browser browser = browser();
        String body = """
                {"phone": "%s", "purpose": "SIGNUP"}""".formatted(phone);

        browser.post("/api/auth/phone/send-code", body).andExpect(status().isOk());
        browser.post("/api/auth/phone/send-code", body)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SMS_RESEND_TOO_SOON"));

        for (int i = 2; i <= 5; i++) {
            advance(Duration.ofSeconds(61));
            browser.post("/api/auth/phone/send-code", body).andExpect(status().isOk());
        }
        advance(Duration.ofSeconds(61));
        browser.post("/api/auth/phone/send-code", body)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SMS_RATE_LIMITED"));

        // 첫 발송에서 1시간이 지나면 다시 받을 수 있다
        advance(Duration.ofHours(1));
        browser.post("/api/auth/phone/send-code", body).andExpect(status().isOk());
    }

    @Test
    void 인증번호를_5번_틀리면_맞는_번호도_실패한다() throws Exception {
        String phone = newPhone();
        Browser browser = browser();
        sendCode(browser, phone, "SIGNUP");
        String code = sms.lastCode(phone);
        String wrongCode = code.equals("000000") ? "111111" : "000000";
        String wrong = """
                {"phone": "%s", "code": "%s"}""".formatted(phone, wrongCode);

        for (int i = 0; i < 4; i++) {
            browser.post("/api/auth/phone/verify", wrong)
                    .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
        }
        browser.post("/api/auth/phone/verify", wrong)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFICATION_ATTEMPTS_EXCEEDED"));

        browser.post("/api/auth/phone/verify", """
                {"phone": "%s", "code": "%s"}""".formatted(phone, code))
                .andExpect(jsonPath("$.code").value("VERIFICATION_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void 인증번호는_3분이_지나면_만료된다() throws Exception {
        String phone = newPhone();
        Browser browser = browser();
        sendCode(browser, phone, "SIGNUP");

        advance(Duration.ofMinutes(3));

        browser.post("/api/auth/phone/verify", """
                {"phone": "%s", "code": "%s"}""".formatted(phone, sms.lastCode(phone)))
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    @Test
    void 입력값이_비면_필드별_오류를_준다() throws Exception {
        browser().post("/api/auth/phone/send-code", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.length()").value(2));
    }

    private static String digits(String phone) {
        return phone.replace("-", "");
    }

    private static String masked(String phone) {
        return phone.substring(0, 4) + "****" + phone.substring(8);
    }
}
