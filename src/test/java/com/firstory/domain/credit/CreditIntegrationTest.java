package com.firstory.domain.credit;

import com.firstory.domain.generation.service.GenerationPoller;
import com.firstory.global.ai.FakeAiClient;
import com.firstory.global.error.ErrorCode;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.firstory.support.StoryFlow;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 동화 이용권 (베타 가입은 열어 둔 상태). 이용권은 생성 확정에서 1회를 쓴다.
 */
@TestPropertySource(properties = {"firstory.beta.credits-enabled=true",
        "firstory.beta.unlimited-emails=" + CreditIntegrationTest.DEV_EMAIL})
class CreditIntegrationTest extends IntegrationTest {

    static final String DEV_EMAIL = "Dev-Unlimited@Example.com";

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
                .andExpect(result -> assertThat(result.getResponse().getHeader("Location")).isEqualTo("/auth"));

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
        parent.get("/api/invite/" + full)
                .andExpect(result -> assertThat(result.getResponse().getHeader("Location"))
                        .isEqualTo("/auth?invite=closed"));
        parent.get("/api/invite/none-such-code")
                .andExpect(result -> assertThat(result.getResponse().getHeader("Location"))
                        .isEqualTo("/auth?invite=closed"));

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
        assertThat(jdbc.queryForObject("SELECT granted_at FROM pre_contacts WHERE email = ?", Object.class, email))
                .isNotNull();
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
        jdbc.update("""
                INSERT INTO pre_contacts (email, credit_amount, credit_expires_at, created_at, updated_at)
                VALUES (?, 2, ?, now(), now())""", email, Timestamp.from(clock.instant().plus(creditsFor)));
    }
}
