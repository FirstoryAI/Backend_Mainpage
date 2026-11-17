package com.firstory.domain.credit;

import com.firstory.global.error.ErrorCode;
import com.firstory.global.kakao.KakaoUser;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 베타 동안(invite-only): 초대 링크, 사전 연락 이메일, 개발용 이메일만 가입한다.
 */
@TestPropertySource(properties = {"firstory.beta.credits-enabled=true", "firstory.beta.invite-only=true",
        "firstory.beta.unlimited-emails=" + BetaInviteOnlyIntegrationTest.DEV_EMAIL})
class BetaInviteOnlyIntegrationTest extends IntegrationTest {

    static final String DEV_EMAIL = "beta-dev@example.com";

    @Test
    void 초대받지_않은_사람은_인증번호도_받지_못한다() throws Exception {
        browser().post("/api/auth/email/send-code", """
                {"email": "%s", "purpose": "SIGNUP"}""".formatted(newEmail()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SIGNUP_INVITE_REQUIRED"))
                .andExpect(jsonPath("$.message").value(ErrorCode.SIGNUP_INVITE_REQUIRED.getMessage()));
    }

    @Test
    void 초대_링크로_들어오면_가입하고_2회를_받는다() throws Exception {
        String code = inviteCode(100);
        Browser parent = browser();
        parent.get("/api/invite/" + code).andExpect(status().isFound());

        signUpIn(parent, newEmail(), allConsents());

        parent.get("/api/me")
                .andExpect(jsonPath("$.signupSource").value("INVITE"))
                .andExpect(jsonPath("$.storiesRemaining").value(2));
    }

    @Test
    void 선착순이_차면_링크도_가입도_막힌다() throws Exception {
        String code = inviteCode(1);
        Browser first = browser();
        Browser late = browser();
        first.get("/api/invite/" + code);
        late.get("/api/invite/" + code);
        signUpIn(first, newEmail(), allConsents());

        // 링크를 먼저 눌러 둔 사람도 가입하는 순간 자리가 없으면 막힌다
        late.post("/api/auth/email/send-code", """
                {"email": "%s", "purpose": "SIGNUP"}""".formatted(newEmail()))
                .andExpect(jsonPath("$.code").value("INVITE_CLOSED"));
        browser().get("/api/invite/" + code)
                .andExpect(result -> assertThat(result.getResponse().getHeader("Location")).endsWith("invite=closed"));
        assertThat(jdbc.queryForObject("SELECT used_count FROM invite_codes WHERE code = ?", Integer.class, code))
                .isOne();
    }

    @Test
    void 인증을_마친_뒤_자리가_차면_가입에서_INVITE_CLOSED() throws Exception {
        String code = inviteCode(1);
        Browser first = browser();
        Browser late = browser();
        first.get("/api/invite/" + code);
        late.get("/api/invite/" + code);
        String lateEmail = newEmail();
        verifyEmail(late, lateEmail, "SIGNUP");
        signUpIn(first, newEmail(), allConsents());

        late.post("/api/auth/signup", signupJson(lateEmail, allConsents()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVITE_CLOSED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, lateEmail)).isZero();
    }

    @Test
    void 사전_연락_이메일은_링크_없이_가입한다() throws Exception {
        String email = newEmail();
        jdbc.update("""
                INSERT INTO pre_contacts (email, credit_amount, credit_expires_at, created_at, updated_at)
                VALUES (?, 2, ?, now(), now())""", email, Timestamp.from(clock.instant().plus(Duration.ofDays(1))));

        Browser parent = signUp(email);

        parent.get("/api/me")
                .andExpect(jsonPath("$.signupSource").value("PRE_CONTACT"))
                .andExpect(jsonPath("$.storiesRemaining").value(2));
    }

    @Test
    void 기한이_지난_사전_연락_이메일은_베타_중에_가입하지_못한다() throws Exception {
        String email = newEmail();
        jdbc.update("""
                INSERT INTO pre_contacts (email, credit_amount, credit_expires_at, created_at, updated_at)
                VALUES (?, 2, ?, now(), now())""", email, Timestamp.from(clock.instant().minus(Duration.ofMinutes(1))));

        browser().post("/api/auth/email/send-code", """
                {"email": "%s", "purpose": "SIGNUP"}""".formatted(email))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SIGNUP_INVITE_REQUIRED"));
    }

    @Test
    void 개발용_이메일은_베타_중에도_가입하고_무제한이다() throws Exception {
        signUp(DEV_EMAIL).get("/api/me")
                .andExpect(jsonPath("$.signupSource").value("DEV"))
                .andExpect(jsonPath("$.unlimitedStories").value(true));
    }

    @Test
    void 카카오_가입도_초대가_있어야_한다() throws Exception {
        String code = "beta-" + UUID.randomUUID();
        kakao.register(code, new KakaoUser(String.valueOf(Math.abs(code.hashCode())), newEmail(), true));
        Browser parent = browser();
        parent.post("/api/auth/kakao/login", """
                {"code": "%s", "redirectUri": "http://localhost:5173/oauth/kakao"}""".formatted(code))
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"));
        parent.post("/api/auth/kakao/signup", """
                {"consents": %s, "termsVersion": "2026-10-01"}""".formatted(allConsents()))
                .andExpect(jsonPath("$.code").value("SIGNUP_INVITE_REQUIRED"));

        parent.get("/api/invite/" + inviteCode(100));
        parent.post("/api/auth/kakao/signup", """
                {"consents": %s, "termsVersion": "2026-10-01"}""".formatted(allConsents()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.signupSource").value("INVITE"))
                .andExpect(jsonPath("$.storiesRemaining").value(2));
    }

    private String inviteCode(int maxUses) {
        String code = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 11);
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO invite_codes (code, max_uses, used_count, closes_at, credit_amount, credit_expires_at,
                                          created_at, updated_at)
                VALUES (?, ?, 0, ?, 2, ?, now(), now())""",
                code, maxUses, Timestamp.from(now.plus(Duration.ofDays(1))), Timestamp.from(now.plus(Duration.ofDays(1))));
        return code;
    }
}
