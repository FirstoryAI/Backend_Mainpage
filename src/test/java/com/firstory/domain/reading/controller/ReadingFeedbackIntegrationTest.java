package com.firstory.domain.reading.controller;

import com.firstory.domain.generation.service.GenerationPoller;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.firstory.support.StoryFlow;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 함께 읽기 끝 피드백 (Backend#76). 끝내면서 그 세션과 동화에 저장하고, 저장하지 못하면 끝내지도 않는다.
 */
class ReadingFeedbackIntegrationTest extends IntegrationTest {

    static final String FEEDBACK = """
            {"feedback": {"childInterest": "매우 흥미로워했어요", "conversationFlow": "평소와 비슷했어요",
             "conversationHelpfulness": "조금 도움이 되었어요", "comment": "  또 읽고 싶대요 "}}""";

    @Autowired
    GenerationPoller poller;

    Browser parent;

    @BeforeEach
    void signUpParent() throws Exception {
        parent = signUp(newEmail());
    }

    @Test
    void 끝내면서_피드백을_코드로_세션과_동화에_저장한다() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long sessionId = startSession(id);

        complete(sessionId, FEEDBACK).andExpect(status().isNoContent());

        Map<String, Object> row = feedback(sessionId);
        assertThat(row.get("story_id")).isEqualTo(id);
        assertThat(row.get("child_interest")).isEqualTo("HIGH");
        assertThat(row.get("conversation_flow")).isEqualTo("USUAL");
        assertThat(row.get("conversation_helpfulness")).isEqualTo("SOMEWHAT");
        assertThat(row.get("comment")).isEqualTo("또 읽고 싶대요");
        assertThat(completedAt(sessionId)).isNotNull();
    }

    @Test
    void 코드로_보내도_되고_빈_의견은_저장하지_않는다() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long sessionId = startSession(id);

        complete(sessionId, """
                {"feedback": {"childInterest": "LOW", "conversationFlow": "NONE",
                 "conversationHelpfulness": "NOT_MUCH", "comment": "   "}}""").andExpect(status().isNoContent());

        Map<String, Object> row = feedback(sessionId);
        assertThat(row.get("child_interest")).isEqualTo("LOW");
        assertThat(row.get("comment")).isNull();
    }

    @Test
    void 다시_끝내도_처음_받은_피드백_하나만_남는다() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long sessionId = startSession(id);

        complete(sessionId, FEEDBACK).andExpect(status().isNoContent());
        complete(sessionId, """
                {"feedback": {"childInterest": "LOW", "conversationFlow": "NONE",
                 "conversationHelpfulness": "NOT_MUCH", "comment": ""}}""").andExpect(status().isNoContent());

        assertThat(feedbackCount(sessionId)).isEqualTo(1);
        assertThat(feedback(sessionId).get("child_interest")).isEqualTo("HIGH");
    }

    @Test
    void 피드백_없이_끝내면_끝내기만_하고_나중에_보낸_피드백은_저장한다() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long sessionId = startSession(id);

        complete(sessionId, null).andExpect(status().isNoContent());
        assertThat(completedAt(sessionId)).isNotNull();
        assertThat(feedbackCount(sessionId)).isZero();

        complete(sessionId, FEEDBACK).andExpect(status().isNoContent());
        assertThat(feedbackCount(sessionId)).isEqualTo(1);
    }

    @Test
    void 선택_답이_빠지면_400이고_끝내지_않는다() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long sessionId = startSession(id);

        complete(sessionId, """
                {"feedback": {"childInterest": "HIGH", "conversationFlow": "NATURAL", "comment": ""}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0].field").value("feedback.conversationHelpfulness"));

        assertThat(completedAt(sessionId)).isNull();
        assertThat(jdbc.queryForObject("SELECT first_read_completed_at FROM stories WHERE id = ?", Object.class, id))
                .isNull();
        assertThat(feedbackCount(sessionId)).isZero();
    }

    @Test
    void 다른_회원의_세션이면_404이고_저장하지_않는다() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long sessionId = startSession(id);
        Browser other = signUp(newEmail());

        other.post("/api/reading-sessions/" + sessionId + "/complete", FEEDBACK)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("READING_SESSION_NOT_FOUND"));

        assertThat(feedbackCount(sessionId)).isZero();
    }

    private long startSession(long id) throws Exception {
        String body = parent.post("/api/stories/" + id + "/reading-sessions", null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.sessionId")).longValue();
    }

    private ResultActions complete(long sessionId, String json) throws Exception {
        return parent.post("/api/reading-sessions/" + sessionId + "/complete", json);
    }

    private Map<String, Object> feedback(long sessionId) {
        return jdbc.queryForMap("SELECT * FROM reading_feedbacks WHERE session_id = ?", sessionId);
    }

    private int feedbackCount(long sessionId) {
        return jdbc.queryForObject("SELECT count(*) FROM reading_feedbacks WHERE session_id = ?", Integer.class,
                sessionId);
    }

    private Object completedAt(long sessionId) {
        return jdbc.queryForObject("SELECT completed_at FROM reading_sessions WHERE id = ?", Object.class, sessionId);
    }
}
