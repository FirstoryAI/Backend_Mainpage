package com.firstory;

import com.firstory.domain.generation.service.GenerationPoller;
import com.firstory.global.ai.FakeAiClient;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프론트가 실제로 부르는 순서대로 가입부터 함께 읽기, 로그아웃까지 한 번에 지나간다.
 * docs/frontend/firstory.http와 scripts/smoke-local.sh도 같은 순서다.
 */
class FullJourneyIntegrationTest extends IntegrationTest {

    static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47};
    static final String CHILD_PROFILE = """
            {"birthYm": "2021-03", "gender": "FEMALE", "traits": ["SHY"], "interestCodes": ["DINOSAUR"],
             "customInterests": ["기차"]}""";

    @Autowired
    GenerationPoller poller;

    @Test
    void 가입부터_함께_읽기와_로그아웃까지() throws Exception {
        String email = newEmail();
        String phone = newPhone();

        // 1. CSRF → 문자 인증 → 가입 → 내 정보
        Browser parent = new Browser(mockMvc);
        parent.get("/api/auth/csrf").andExpect(status().isNoContent());
        assertThat(parent.hasCookie(Browser.XSRF)).isTrue();
        parent.post("/api/auth/phone/send-code", """
                {"phone": "%s", "purpose": "SIGNUP"}""".formatted(phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(180));
        parent.post("/api/auth/phone/verify", """
                {"phone": "%s", "code": "%s"}""".formatted(phone, sms.lastCode(phone)))
                .andExpect(status().isNoContent());
        parent.post("/api/auth/signup", signupJson(email, phone, allConsents())).andExpect(status().isCreated());
        parent.get("/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phoneMasked").value(phone.substring(0, 4) + "****" + phone.substring(8)))
                .andExpect(jsonPath("$.onboardingSeen").value(false));
        parent.post("/api/me/onboarding-seen", null).andExpect(status().isNoContent());

        // 2. 아이 프로필 저장
        long childId = id(parent.post("/api/children", CHILD_PROFILE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("아이 A")), "$.id");

        // 3. 동화 만들기: 상황 → 아이(저장된 프로필) → 전하고 싶은 말 → 인터뷰
        long storyId = id(parent.post("/api/stories", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT")), "$.storyId");
        String story = "/api/stories/" + storyId;
        parent.put(story + "/situation", """
                {"timing": "PAST", "category": "NEW_ENVIRONMENT", "description": "새 어린이집에 갔어요",
                 "reactions": ["AVOIDED"], "emotions": ["SCARED"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.safety.action").value("PROCEED"));
        parent.put(story + "/child", """
                {"childId": %d, "saveForNextTime": false, "profile": %s}""".formatted(childId, CHILD_PROFILE))
                .andExpect(jsonPath("$.saved").value(true));
        parent.put(story + "/message", """
                {"message": "처음엔 떨려도 괜찮다고 말해 주고 싶어요"}""")
                .andExpect(jsonPath("$.saved").value(true));
        parent.post(story + "/interview/start", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEWING"))
                .andExpect(jsonPath("$.turn").value(1))
                .andExpect(jsonPath("$.question").value(FakeAiClient.FIRST_QUESTION));
        String status = "INTERVIEWING";
        int answers = 0;
        while (status.equals("INTERVIEWING")) {
            String body = parent.post(story + "/interview/answer", """
                    {"answer": "문 앞에서 한참 서 있었어요"}""")
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            status = JsonPath.read(body, "$.status");
            answers++;
            assertThat(answers).as("질문은 5개를 넘지 않는다").isLessThanOrEqualTo(5);
        }
        assertThat(status).isEqualTo("CONFIRMING");
        parent.get(story + "/draft")
                .andExpect(jsonPath("$.child.childId").value(childId))
                .andExpect(jsonPath("$.interview.turns", hasSize(answers)))
                .andExpect(jsonPath("$.interview.summary").value(FakeAiClient.SUMMARY));

        // 4. 확인 → 생성 진행 → 미리보기 → 그림 한 장
        parent.post(story + "/confirm", null)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
        poller.pollOnce();
        parent.get(story + "/generation")
                .andExpect(jsonPath("$.status").value("GENERATING_TEXT"))
                .andExpect(jsonPath("$.stage").value(1));
        pollUntilReviewing(story);
        String preview = parent.get(story + "/preview")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWING"))
                .andExpect(jsonPath("$.version.versionNo").value(1))
                .andExpect(jsonPath("$.version.title").value(FakeAiClient.SAMPLE.title(0)))
                .andExpect(jsonPath("$.pages", hasSize(8)))
                .andExpect(jsonPath("$.pages[1].text").value(FakeAiClient.SAMPLE.pageText(2)))
                .andExpect(jsonPath("$.regenerateRemaining").value(3))
                .andReturn().getResponse().getContentAsString();
        long version1 = ((Number) JsonPath.read(preview, "$.version.id")).longValue();
        String imageUrl = JsonPath.read(preview, "$.pages[0].imageUrl");
        MvcResult image = parent.get(imageUrl)
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(image.getResponse().getContentAsByteArray()).startsWith(PNG_SIGNATURE);

        // 5. 그림만 다시(3쪽) → 버전 2 → 버전 1로 되돌리기 → 확정
        parent.post(story + "/regenerate", """
                {"scope": "IMAGES", "pages": [3]}""")
                .andExpect(status().isAccepted());
        pollUntilReviewing(story);
        parent.get(story + "/preview")
                .andExpect(jsonPath("$.version.versionNo").value(2))
                .andExpect(jsonPath("$.version.title").value(FakeAiClient.SAMPLE.title(1)))
                .andExpect(jsonPath("$.regenerateRemaining").value(2));
        parent.get(story + "/versions")
                .andExpect(jsonPath("$[*].versionNo", contains(1, 2)))
                .andExpect(jsonPath("$[*].current", contains(false, true)));
        parent.put(story + "/current-version", """
                {"versionId": %d}""".formatted(version1))
                .andExpect(status().isOk());
        parent.get(story + "/preview").andExpect(jsonPath("$.version.versionNo").value(1));
        parent.post(story + "/approve", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());

        // 6. 홈, 서재(UNREAD), 상세, 가이드
        parent.get("/api/home")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingSeen").value(true))
                .andExpect(jsonPath("$.counts.unread").value(1))
                .andExpect(jsonPath("$.recent[0].id").value(storyId));
        parent.get("/api/stories?filter=UNREAD")
                .andExpect(jsonPath("$.items[*].id", contains((int) storyId)));
        parent.get(story)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(FakeAiClient.SAMPLE.title(0)))
                .andExpect(jsonPath("$.childDisplayName").value("아이 A"))
                .andExpect(jsonPath("$.canRead").value(true))
                .andExpect(jsonPath("$.readCount").value(0));
        String guide = parent.get(story + "/guide")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompts", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        long promptId = ((Number) JsonPath.read(guide, "$.prompts[0].id")).longValue();

        // 7. 읽기 시작 → 쪽 넘김 → 대화 버튼 → 다 읽음 → 서재 READ
        parent.get(story + "/book")
                .andExpect(jsonPath("$.versionId").value(version1))
                .andExpect(jsonPath("$.pages", hasSize(8)));
        long sessionId = id(parent.post(story + "/reading-sessions", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastPage").value(0)), "$.sessionId");
        String session = "/api/reading-sessions/" + sessionId;
        for (int page = 1; page <= 4; page++) {
            parent.put(session + "/progress", """
                    {"page": %d}""".formatted(page)).andExpect(status().isNoContent());
        }
        parent.put(session + "/dialogue/" + promptId, """
                {"action": "TALKED"}""").andExpect(status().isNoContent());
        parent.get(story + "/reading-sessions/resume")
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.lastPage").value(4));
        for (int page = 5; page <= 8; page++) {
            parent.put(session + "/progress", """
                    {"page": %d}""".formatted(page)).andExpect(status().isNoContent());
        }
        parent.post(session + "/complete", null).andExpect(status().isNoContent());
        parent.get("/api/stories?filter=UNREAD").andExpect(jsonPath("$.items", hasSize(0)));
        parent.get("/api/stories?filter=READ").andExpect(jsonPath("$.items[*].id", contains((int) storyId)));
        parent.get(story).andExpect(jsonPath("$.readCount").value(1));

        // 8. 로그아웃 → 401
        parent.post("/api/auth/logout", null).andExpect(status().isNoContent());
        parent.get("/api/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // 스케줄러가 꺼져 있어 직접 진행시킨다. 처음 만들기는 5번, 그림만 다시는 3번
    private void pollUntilReviewing(String story) throws Exception {
        for (int i = 0; i < 10; i++) {
            poller.pollOnce();
            String status = jdbc.queryForObject("SELECT status FROM stories WHERE id = ?", String.class,
                    Long.parseLong(story.substring(story.lastIndexOf('/') + 1)));
            if (List.of("REVIEWING", "FAILED").contains(status)) {
                assertThat(status).isEqualTo("REVIEWING");
                return;
            }
        }
        throw new AssertionError("REVIEWING이 되지 않았어요");
    }

    private static long id(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }
}
