package com.firstory.domain.auth.controller;

import com.firstory.global.kakao.FakeKakaoClient;
import com.firstory.global.kakao.KakaoUser;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KakaoLoginIntegrationTest extends IntegrationTest {

    private static final String REDIRECT_URI = "http://localhost:5173/oauth/kakao";

    @Test
    void 처음이면_가입하고_다음부터는_바로_로그인된다() throws Exception {
        String email = newEmail();
        String code = registerKakao(email, true);
        Browser browser = browser();

        kakaoLogin(browser, code, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.me").doesNotExist());
        browser.get("/api/me").andExpect(status().isUnauthorized());

        kakaoSignup(browser, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.kakaoLinked").value(true))
                .andExpect(jsonPath("$.hasPassword").value(false));
        browser.get("/api/me").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email)).isNull();
        assertThat(jdbc.queryForList("SELECT consent_type FROM user_consents uc JOIN users u ON u.id = uc.user_id "
                + "WHERE u.email = ?", String.class, email))
                .containsExactlyInAnyOrder("GUARDIAN", "TERMS", "PRIVACY", "EMAIL_NOTIFY");

        Browser again = browser();
        kakaoLogin(again, code, true)
                .andExpect(jsonPath("$.status").value("LOGGED_IN"))
                .andExpect(jsonPath("$.me.email").value(email));
        again.get("/api/me").andExpect(status().isOk());
        assertThat(again.hasCookie(Browser.REMEMBER_ME)).isTrue();
    }

    @Test
    void 카카오가_인증하지_않은_이메일은_우리_이메일_인증을_마쳐야_가입한다() throws Exception {
        String kakaoEmail = newEmail();
        String code = registerKakao(kakaoEmail, false);
        Browser browser = browser();
        kakaoLogin(browser, code, false)
                .andExpect(jsonPath("$.email").value(kakaoEmail))
                .andExpect(jsonPath("$.emailVerified").value(false));

        kakaoSignup(browser, kakaoEmail).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        kakaoSignup(browser, null).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        // 카카오 이메일 대신 다른 이메일로 인증해도 된다
        String other = newEmail();
        verifyEmail(browser, other, "SIGNUP");
        kakaoSignup(browser, other)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(other))
                .andExpect(jsonPath("$.kakaoLinked").value(true));
    }

    @Test
    void 카카오가_이메일을_주지_않아도_이메일_인증으로_가입한다() throws Exception {
        String code = "no-email-" + UUID.randomUUID();
        kakao.register(code, new KakaoUser(String.valueOf(code.hashCode()), null, false));
        Browser browser = browser();
        kakaoLogin(browser, code, false)
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.emailVerified").value(false));

        String email = newEmail();
        verifyEmail(browser, email, "SIGNUP");
        kakaoSignup(browser, email).andExpect(status().isCreated());
    }

    @Test
    void 같은_이메일로_이메일_가입한_회원이_있으면_합치지_않고_409() throws Exception {
        String email = newEmail();
        signUp(email);
        String code = registerKakao(email.toUpperCase(), true);

        Browser browser = browser();
        kakaoLogin(browser, code, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KAKAO_EMAIL_REGISTERED"));
        kakaoSignup(browser, null).andExpect(jsonPath("$.code").value("KAKAO_SIGNUP_EXPIRED"));
    }

    @Test
    void 가입한_카카오_계정으로는_다시_가입할_수_없다() throws Exception {
        String code = registerKakao(newEmail(), true);
        Browser first = browser();
        Browser second = browser();
        kakaoLogin(first, code, false);
        kakaoLogin(second, code, false);

        kakaoSignup(first, null).andExpect(status().isCreated());
        kakaoSignup(second, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KAKAO_ACCOUNT_REGISTERED"));
    }

    @Test
    void 카카오_로그인_없이_또는_10분이_지나_가입하면_KAKAO_SIGNUP_EXPIRED() throws Exception {
        kakaoSignup(browser(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KAKAO_SIGNUP_EXPIRED"));

        Browser browser = browser();
        kakaoLogin(browser, registerKakao(newEmail(), true), false);
        advance(Duration.ofMinutes(10));
        kakaoSignup(browser, null).andExpect(jsonPath("$.code").value("KAKAO_SIGNUP_EXPIRED"));
    }

    @Test
    void 필수_동의가_빠지면_가입하지_않는다() throws Exception {
        Browser browser = browser();
        kakaoLogin(browser, registerKakao(newEmail(), true), false);

        browser.post("/api/auth/kakao/signup", """
                {"consents": {"GUARDIAN": true, "TERMS": true}, "termsVersion": "2026-10-01"}""")
                .andExpect(jsonPath("$.code").value("REQUIRED_CONSENT_MISSING"));
        kakaoSignup(browser, null).andExpect(status().isCreated());
    }

    @Test
    void 카카오가_코드를_거절하면_KAKAO_LOGIN_FAILED() throws Exception {
        kakaoLogin(browser(), FakeKakaoClient.FAILING_CODE, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KAKAO_LOGIN_FAILED"));
        browser().post("/api/auth/kakao/login", "{}")
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.length()").value(2));
    }

    private String registerKakao(String email, boolean verified) {
        String code = "code-" + UUID.randomUUID();
        kakao.register(code, new KakaoUser(String.valueOf(Math.abs(code.hashCode())), email, verified));
        return code;
    }

    private ResultActions kakaoLogin(Browser browser, String code, boolean rememberMe) throws Exception {
        return browser.post("/api/auth/kakao/login", """
                {"code": "%s", "redirectUri": "%s", "rememberMe": %s}""".formatted(code, REDIRECT_URI, rememberMe));
    }

    private ResultActions kakaoSignup(Browser browser, String email) throws Exception {
        String emailField = email == null ? "" : "\"email\": \"%s\", ".formatted(email);
        return browser.post("/api/auth/kakao/signup", """
                {%s"consents": %s, "termsVersion": "2026-10-01"}""".formatted(emailField, allConsents()));
    }
}
