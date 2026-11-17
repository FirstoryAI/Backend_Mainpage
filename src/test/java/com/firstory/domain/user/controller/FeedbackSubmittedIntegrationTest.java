package com.firstory.domain.user.controller;

import com.firstory.domain.generation.service.GenerationPoller;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.firstory.support.StoryFlow;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/me의 feedbackSubmitted (Backend#82). 종류마다 한 번이라도 저장에 성공하면 true, 회원마다 따로.
 */
class FeedbackSubmittedIntegrationTest extends IntegrationTest {

    static final String PREVIEW_FEEDBACK = """
            {"feedback": {"situationReflection": "WELL", "storySatisfaction": "SATISFIED",
             "readingIntent": "DEFINITELY", "comment": ""}}""";
    static final String READING_FEEDBACK = """
            {"feedback": {"childInterest": "HIGH", "conversationFlow": "NATURAL",
             "conversationHelpfulness": "VERY", "comment": ""}}""";

    @Autowired
    GenerationPoller poller;

    Browser parent;

    @BeforeEach
    void signUpParent() throws Exception {
        parent = signUp(newEmail());
    }

    @Test
    void 처음엔_둘_다_false() throws Exception {
        expectSubmitted(parent, false, false);
    }

    @Test
    void 확정_피드백을_내면_preview만_true이고_다음_동화는_피드백_없이_확정된다() throws Exception {
        long first = StoryFlow.reviewing(parent, poller);
        parent.post(StoryFlow.url(first) + "/approve", PREVIEW_FEEDBACK).andExpect(status().isOk());

        expectSubmitted(parent, true, false);

        long second = StoryFlow.reviewing(parent, poller);
        parent.post(StoryFlow.url(second) + "/approve", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        expectSubmitted(parent, true, false);
    }

    @Test
    void 읽기_피드백을_내면_reading만_true이고_다음_읽기는_피드백_없이_끝난다() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        parent.post("/api/reading-sessions/" + startSession(id) + "/complete", READING_FEEDBACK)
                .andExpect(status().isNoContent());

        expectSubmitted(parent, false, true);

        parent.post("/api/reading-sessions/" + startSession(id) + "/complete", null)
                .andExpect(status().isNoContent());
        expectSubmitted(parent, false, true);
    }

    @Test
    void 저장에_실패하면_false_그대로() throws Exception {
        long id = StoryFlow.reviewing(parent, poller);
        parent.post(StoryFlow.url(id) + "/approve", """
                {"feedback": {"situationReflection": "WELL", "comment": ""}}""")
                .andExpect(status().isBadRequest());

        expectSubmitted(parent, false, false);
    }

    @Test
    void 피드백을_낸_동화를_지워도_true이고_다른_회원은_false() throws Exception {
        long id = StoryFlow.reviewing(parent, poller);
        parent.post(StoryFlow.url(id) + "/approve", PREVIEW_FEEDBACK).andExpect(status().isOk());
        parent.post("/api/reading-sessions/" + startSession(id) + "/complete", READING_FEEDBACK)
                .andExpect(status().isNoContent());
        parent.delete(StoryFlow.url(id)).andExpect(status().is2xxSuccessful());

        expectSubmitted(parent, true, true);
        expectSubmitted(signUp(newEmail()), false, false);
    }

    private void expectSubmitted(Browser browser, boolean preview, boolean reading) throws Exception {
        browser.get("/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackSubmitted.preview").value(preview))
                .andExpect(jsonPath("$.feedbackSubmitted.reading").value(reading));
    }

    private long startSession(long id) throws Exception {
        String body = parent.post("/api/stories/" + id + "/reading-sessions", null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.sessionId")).longValue();
    }
}
