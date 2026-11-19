package com.firstory.domain.credit;

import com.firstory.domain.generation.service.GenerationPoller;
import com.firstory.global.ai.FakeAiClient;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.kakao.KakaoUser;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.firstory.support.StoryFlow;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultMatcher;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 동화 이용권 (베타 가입은 열어 둔 상태). 이용권은 생성 확정에서 1회를 쓴다.
 */
@TestPropertySource(properties = {"firstory.beta.credits-enabled=true",
        "firstory.beta.unlimited-emails=" + CreditIntegrationTest.DEV_EMAIL + "," + CreditIntegrationTest.DEV_HOME_EMAIL})
class CreditIntegrationTest extends IntegrationTest {

    static final String DEV_EMAIL = "Dev-Unlimited@Example.com";
    static final String DEV_HOME_EMAIL = "dev-home@example.com";

    @Autowired
    GenerationPoller poller;

    @Autowired
    FakeAiClient ai;

    @AfterEach
    void clearAi() {
        ai.clearFailure();
    }

    @Test
    void 일반_가입은_무료_1회이고_다_쓰면_동화_만들기_시작에서_막힌다() throws Exception {
        Browser parent = signUp(newEmail());
        parent.get("/api/me")
                .andExpect(jsonPath("$.signupSource").value("GENERAL"))
                .andExpect(jsonPath("$.unlimitedStories").value(false))
                .andExpect(jsonPath("$.storiesRemaining").value(1));

        long id = StoryFlow.reviewing(parent, poller);
        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(0));

        // 같은 동화의 다시 만들기는 이용권을 쓰지 않는다
        parent.post(StoryFlow.url(id) + "/regenerate", """
                {"scope": "TEXT"}""").andExpect(status().isAccepted());

        parent.post("/api/stories", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORY_LIMIT_REACHED"))
                .andExpect(jsonPath("$.message").value(ErrorCode.STORY_LIMIT_REACHED.getMessage()));
    }

    @Test
    void 시작한_뒤_다른_동화로_다_썼으면_생성_확정에서_막힌다() throws Exception {
        Browser parent = signUp(newEmail());
        long first = StoryFlow.confirming(parent, "용기를 주고 싶어요");
        long second = StoryFlow.confirming(parent, "괜찮다고 말해 주고 싶어요");

        parent.post(StoryFlow.url(first) + "/confirm", null).andExpect(status().isAccepted());
        parent.post(StoryFlow.url(second) + "/confirm", null)
                .andExpect(jsonPath("$.code").value("STORY_LIMIT_REACHED"));
        parent.get(StoryFlow.url(second) + "/draft").andExpect(jsonPath("$.status").value("CONFIRMING"));
    }

    @Test
    void AI가_생성을_받지_못하면_이용권을_되돌린다() throws Exception {
        Browser parent = signUp(newEmail());
        long id = StoryFlow.confirming(parent, "용기를 주고 싶어요");
        ai.failWith(ErrorCode.AI_UNAVAILABLE);

        parent.post(StoryFlow.url(id) + "/confirm", null).andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));

        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(1));
        ai.clearFailure();
        parent.post(StoryFlow.url(id) + "/confirm", null).andExpect(status().isAccepted());
        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(0));
    }

    @Test
    void 첫_생성의_완성_본문이_막은_주제라_실패하면_이용권을_되돌린다() throws Exception {
        Browser parent = signUp(newEmail());
        long id = StoryFlow.confirming(parent, "이야기 " + FakeAiClient.OUTPUT_SEPARATION_MARKER);
        parent.post(StoryFlow.url(id) + "/confirm", null).andExpect(status().isAccepted());
        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(0));

        for (int i = 0; i < 5; i++) {
            poller.pollOnce();
        }

        parent.get(StoryFlow.url(id) + "/generation").andExpect(jsonPath("$.error").value("OUTPUT_UNSAFE"));
        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(1));
    }

    @Test
    void 동화를_지워도_쓴_횟수는_돌아오지_않는다() throws Exception {
        Browser parent = signUp(newEmail());
        long id = StoryFlow.reviewing(parent, poller);

        parent.delete(StoryFlow.url(id)).andExpect(status().isNoContent());

        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(0));
    }

    @Test
    void 개발용_이메일은_이용권_없이_무제한이다() throws Exception {
        Browser dev = signUp(DEV_EMAIL.toLowerCase());
        dev.get("/api/me")
                .andExpect(jsonPath("$.signupSource").value("DEV"))
                .andExpect(jsonPath("$.unlimitedStories").value(true))
                .andExpect(jsonPath("$.storiesRemaining").doesNotExist());

        for (int i = 0; i < 3; i++) {
            long id = StoryFlow.confirming(dev, "용기를 주고 싶어요");
            dev.post(StoryFlow.url(id) + "/confirm", null).andExpect(status().isAccepted());
        }
    }

    @Test
    void 초대_링크로_가입하면_2회만_받고_일반_1회는_없다() throws Exception {
        String code = inviteCode(100, Duration.ofDays(1), Duration.ofDays(1));
        Browser parent = browser();
        parent.get("/api/invite/" + code)
                .andExpect(status().isFound())
                .andExpect(redirectsTo("/auth"));

        String email = newEmail();
        signUpIn(parent, email, allConsents());

        parent.get("/api/me")
                .andExpect(jsonPath("$.signupSource").value("INVITE"))
                .andExpect(jsonPath("$.storiesRemaining").value(2));
        assertThat(jdbc.queryForList("""
                SELECT c.source FROM story_credits c JOIN users u ON u.id = c.user_id WHERE u.email = ?""",
                String.class, email)).containsExactly("INVITE");
        assertThat(jdbc.queryForObject("SELECT used_count FROM invite_codes WHERE code = ?", Integer.class, code))
                .isOne();
    }

    @Test
    void 초대_이용권은_기한이_지나면_쓸_수_없다() throws Exception {
        String code = inviteCode(100, Duration.ofDays(10), Duration.ofDays(1));
        Browser parent = browser();
        parent.get("/api/invite/" + code);
        signUpIn(parent, newEmail(), allConsents());

        advance(Duration.ofDays(1));

        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(0));
        parent.post("/api/stories", null).andExpect(jsonPath("$.code").value("STORY_LIMIT_REACHED"));
    }

    @Test
    void 마감된_초대_링크는_표시를_남기지_않고_일반_가입이_된다() throws Exception {
        String full = inviteCode(0, Duration.ofDays(1), Duration.ofDays(1));
        Browser parent = browser();
        parent.get("/api/invite/" + full).andExpect(redirectsTo("/auth?invite=closed"));
        parent.get("/api/invite/none-such-code").andExpect(redirectsTo("/auth?invite=closed"));

        signUpIn(parent, newEmail(), allConsents());

        parent.get("/api/me")
                .andExpect(jsonPath("$.signupSource").value("GENERAL"))
                .andExpect(jsonPath("$.storiesRemaining").value(1));
    }

    @Test
    void 사전_연락한_기존_회원은_로그인하면_일반_1회_대신_2회를_받는다() throws Exception {
        String email = newEmail();
        Browser parent = signUp(email);
        preContact(email, Duration.ofDays(1));

        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(2));
        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(2));
        assertThat(preContactGrantedAt(email)).isNotNull();
    }

    @Test
    void 사전_연락_이메일로_가입하면_2회를_받는다() throws Exception {
        String email = newEmail();
        preContact(email, Duration.ofDays(1));

        Browser parent = signUp(email);

        parent.get("/api/me")
                .andExpect(jsonPath("$.signupSource").value("PRE_CONTACT"))
                .andExpect(jsonPath("$.storiesRemaining").value(2));
        String me = parent.get("/api/me").andReturn().getResponse().getContentAsString();
        long userId = ((Number) JsonPath.read(me, "$.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT granted_user_id FROM pre_contacts WHERE email = ?", Long.class, email))
                .isEqualTo(userId);
    }

    @Test
    void 사전_연락_이용권은_기한이_지나면_남아도_쓸_수_없다() throws Exception {
        String email = newEmail();
        preContact(email, Duration.ofDays(1));
        Browser parent = signUp(email);

        advance(Duration.ofDays(1));

        // 무료는 한 종류만이라 일반 1회도 없다
        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(0));
        parent.post("/api/stories", null).andExpect(jsonPath("$.code").value("STORY_LIMIT_REACHED"));
    }

    @Test
    void 마감_전에_받은_사전_연락_이용권은_마감_뒤에도_기한까지_쓴다() throws Exception {
        String email = newEmail();
        preContact(email, Duration.ofDays(5), Duration.ofDays(40));
        Browser parent = signUp(email);

        advance(Duration.ofDays(6));
        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(2));
        parent.post("/api/stories", null).andExpect(status().isCreated());

        advance(Duration.ofDays(34));
        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(0));
    }

    @Test
    void 받을_수_있는_마감이_지나면_이용권_기한이_남아도_일반_가입이다() throws Exception {
        String email = newEmail();
        preContact(email, Duration.ofDays(5), Duration.ofDays(40));
        advance(Duration.ofDays(5));

        signUp(email).get("/api/me")
                .andExpect(jsonPath("$.signupSource").value("GENERAL"))
                .andExpect(jsonPath("$.storiesRemaining").value(1));
    }

    @Test
    void 기한이_지난_뒤_사전_연락_이메일로_가입하면_일반_가입이다() throws Exception {
        String email = newEmail();
        preContact(email, Duration.ofDays(1));
        advance(Duration.ofDays(1));

        Browser parent = signUp(email);

        parent.get("/api/me")
                .andExpect(jsonPath("$.signupSource").value("GENERAL"))
                .andExpect(jsonPath("$.storiesRemaining").value(1));
        assertThat(preContactGrantedAt(email)).isNull();
    }

    @Test
    void 기한이_지난_뒤_처음_로그인한_사전_연락_회원은_일반_1회가_그대로다() throws Exception {
        String email = newEmail();
        Browser parent = signUp(email);
        preContact(email, Duration.ofDays(1));
        advance(Duration.ofDays(1));

        parent.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(1));
        parent.post("/api/stories", null).andExpect(status().isCreated());
        assertThat(preContactGrantedAt(email)).isNull();
    }

    @Test
    void 홈_credits는_일반_가입이면_1분의_1이고_생성하면_줄어든다() throws Exception {
        Browser parent = signUp(newEmail());
        parent.get("/api/home")
                .andExpect(jsonPath("$.credits.beta").value(false))
                .andExpect(jsonPath("$.credits.unlimited").value(false))
                .andExpect(jsonPath("$.credits.remaining").value(1))
                .andExpect(jsonPath("$.credits.total").value(1));

        StoryFlow.reviewing(parent, poller);

        parent.get("/api/home")
                .andExpect(jsonPath("$.credits.remaining").value(0))
                .andExpect(jsonPath("$.credits.total").value(1));
    }

    @Test
    void 홈_credits는_일반_1회를_쓴_뒤_사전_연락을_받으면_2분의_2다() throws Exception {
        String email = newEmail();
        Browser parent = signUp(email);
        StoryFlow.reviewing(parent, poller);
        preContact(email, Duration.ofDays(1));

        // 홈을 여는 것만으로 사전 연락 이용권을 받는다
        parent.get("/api/home")
                .andExpect(jsonPath("$.credits.remaining").value(2))
                .andExpect(jsonPath("$.credits.total").value(2));
    }

    @Test
    void 홈_credits는_초대_이용권_기한이_지나면_0분의_0이다() throws Exception {
        String code = inviteCode(100, Duration.ofDays(10), Duration.ofDays(1));
        Browser parent = browser();
        parent.get("/api/invite/" + code);
        signUpIn(parent, newEmail(), allConsents());
        parent.get("/api/home")
                .andExpect(jsonPath("$.credits.remaining").value(2))
                .andExpect(jsonPath("$.credits.total").value(2));

        advance(Duration.ofDays(1));

        parent.get("/api/home")
                .andExpect(jsonPath("$.credits.remaining").value(0))
                .andExpect(jsonPath("$.credits.total").value(0));
    }

    @Test
    void 홈_credits는_개발용_이메일이면_무제한이고_횟수가_없다() throws Exception {
        signUp(DEV_HOME_EMAIL).get("/api/home")
                .andExpect(jsonPath("$.credits.unlimited").value(true))
                .andExpect(jsonPath("$.credits.remaining").doesNotExist())
                .andExpect(jsonPath("$.credits.total").doesNotExist());
    }

    @Test
    void 탈퇴하고_다시_가입하면_새_무료_없이_예전_1회를_이어_받는다() throws Exception {
        String email = newEmail();
        withdraw(signUp(email));

        Browser back = signUp(email);

        back.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(1));
        back.get("/api/home")
                .andExpect(jsonPath("$.credits.remaining").value(1))
                .andExpect(jsonPath("$.credits.total").value(1));
        assertThat(creditSources(email)).containsExactly("SIGNUP_FREE");
        assertThat(creditRowsOfEmail(email)).isOne();
    }

    @Test
    void 다_쓰고_탈퇴해도_다시_가입하면_0회다() throws Exception {
        String email = newEmail();
        Browser parent = signUp(email);
        StoryFlow.reviewing(parent, poller);
        withdraw(parent);

        Browser back = signUp(email);

        back.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(0));
        back.get("/api/home")
                .andExpect(jsonPath("$.credits.remaining").value(0))
                .andExpect(jsonPath("$.credits.total").value(1));
        back.post("/api/stories", null).andExpect(jsonPath("$.code").value("STORY_LIMIT_REACHED"));
    }

    @Test
    void 초대_2회_중_1회를_쓰고_다시_가입하면_1분의_2다() throws Exception {
        String email = newEmail();
        Browser parent = browser();
        parent.get("/api/invite/" + inviteCode(100, Duration.ofDays(10), Duration.ofDays(10)));
        signUpIn(parent, email, allConsents());
        StoryFlow.reviewing(parent, poller);
        withdraw(parent);

        Browser back = browser();
        back.get("/api/invite/" + inviteCode(100, Duration.ofDays(10), Duration.ofDays(10)));
        signUpIn(back, email, allConsents());

        back.get("/api/home")
                .andExpect(jsonPath("$.credits.remaining").value(1))
                .andExpect(jsonPath("$.credits.total").value(2));
        assertThat(creditSources(email)).containsExactly("INVITE");
    }

    @Test
    void 기한이_지난_예전_이용권은_0이고_새_무료도_없다() throws Exception {
        String email = newEmail();
        Browser parent = browser();
        parent.get("/api/invite/" + inviteCode(100, Duration.ofDays(10), Duration.ofDays(1)));
        signUpIn(parent, email, allConsents());
        withdraw(parent);
        advance(Duration.ofDays(1));

        Browser back = browser();
        back.get("/api/invite/" + inviteCode(100, Duration.ofDays(10), Duration.ofDays(10)));
        signUpIn(back, email, allConsents());

        back.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(0));
        back.get("/api/home")
                .andExpect(jsonPath("$.credits.remaining").value(0))
                .andExpect(jsonPath("$.credits.total").value(0));
        assertThat(creditSources(email)).containsExactly("INVITE");
    }

    @Test
    void 탈퇴_계정이_여럿이면_가장_최근_계정_것만_이어_받는다() throws Exception {
        String email = newEmail();
        withdraw(signUp(email));
        withdraw(signUp(email));
        // 고치기 전에 재가입해 오래된 계정도 따로 무료를 갖고 있는 경우를 흉내 낸다
        jdbc.update("""
                INSERT INTO story_credits (user_id, source, amount, created_at, updated_at)
                SELECT min(id), 'SIGNUP_FREE', 1, now(), now() FROM users WHERE email = ?""", email);

        Browser back = signUp(email);

        back.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(1));
        assertThat(creditSources(email)).hasSize(1);
        // 처음 받은 1회가 계정을 따라 옮겨 다녔고, 오래된 계정 것은 그 자리에 남는다
        assertThat(creditRowsOfEmail(email)).isEqualTo(2);
    }

    @Test
    void 사전_연락을_아직_안_받은_이메일은_재가입하면_사전_연락_2회다() throws Exception {
        String email = newEmail();
        withdraw(signUp(email));
        preContact(email, Duration.ofDays(1));

        Browser back = signUp(email);

        back.get("/api/me").andExpect(jsonPath("$.signupSource").value("PRE_CONTACT"));
        back.get("/api/home")
                .andExpect(jsonPath("$.credits.remaining").value(2))
                .andExpect(jsonPath("$.credits.total").value(2));
        assertThat(preContactGrantedAt(email)).isNotNull();
    }

    @Test
    void 카카오로_다시_가입해도_이어_받는다() throws Exception {
        String email = newEmail();
        String code = "resignup-" + UUID.randomUUID();
        kakao.register(code, new KakaoUser(String.valueOf(Math.abs(code.hashCode())), email, true));
        Browser parent = kakaoSignUp(code);
        StoryFlow.reviewing(parent, poller);
        parent.post("/api/me/withdraw", "{}").andExpect(status().isNoContent());

        Browser back = kakaoSignUp(code);

        back.get("/api/me").andExpect(jsonPath("$.storiesRemaining").value(0));
        assertThat(creditSources(email)).hasSize(1);
    }

    private Browser kakaoSignUp(String code) throws Exception {
        Browser parent = browser();
        parent.post("/api/auth/kakao/login", """
                {"code": "%s", "redirectUri": "http://localhost:5173/oauth/kakao"}""".formatted(code))
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"));
        parent.post("/api/auth/kakao/signup", """
                {"consents": %s, "termsVersion": "2026-10-01"}""".formatted(allConsents()))
                .andExpect(status().isCreated());
        return parent;
    }

    /** 탈퇴하고, 같은 이메일로 인증번호를 다시 받을 수 있게(60초) 시간을 옮긴다 */
    private void withdraw(Browser parent) throws Exception {
        parent.post("/api/me/withdraw", """
                {"currentPassword": "%s"}""".formatted(PASSWORD)).andExpect(status().isNoContent());
        advance(Duration.ofMinutes(2));
    }

    /** 그 이메일의 지금(탈퇴하지 않은) 회원이 가진 이용권 종류 */
    private List<String> creditSources(String email) {
        return jdbc.queryForList("""
                SELECT c.source FROM story_credits c JOIN users u ON u.id = c.user_id
                WHERE u.email = ? AND u.deleted_at IS NULL ORDER BY c.id""", String.class, email);
    }

    /** 그 이메일로 가입했던 모든 계정(탈퇴 포함)의 이용권 행 수 */
    private int creditRowsOfEmail(String email) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM story_credits c JOIN users u ON u.id = c.user_id WHERE u.email = ?""",
                Integer.class, email);
    }

    private static ResultMatcher redirectsTo(String location) {
        return result -> assertThat(result.getResponse().getHeader("Location")).isEqualTo(location);
    }

    private Object preContactGrantedAt(String email) {
        return jdbc.queryForObject("SELECT granted_at FROM pre_contacts WHERE email = ?", Object.class, email);
    }

    private String inviteCode(int maxUses, Duration closesIn, Duration creditsFor) {
        String code = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 11);
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO invite_codes (code, max_uses, used_count, closes_at, credit_amount, credit_expires_at,
                                          created_at, updated_at)
                VALUES (?, ?, 0, ?, 2, ?, now(), now())""",
                code, maxUses, Timestamp.from(now.plus(closesIn)), Timestamp.from(now.plus(creditsFor)));
        return code;
    }

    private void preContact(String email, Duration creditsFor) {
        preContact(email, creditsFor, creditsFor);
    }

    private void preContact(String email, Duration closesIn, Duration creditsFor) {
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO pre_contacts (email, credit_amount, closes_at, credit_expires_at, created_at, updated_at)
                VALUES (?, 2, ?, ?, now(), now())""",
                email, Timestamp.from(now.plus(closesIn)), Timestamp.from(now.plus(creditsFor)));
    }
}
