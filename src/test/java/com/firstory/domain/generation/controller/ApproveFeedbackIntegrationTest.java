package com.firstory.domain.generation.controller;

import com.firstory.domain.generation.service.GenerationPoller;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.firstory.support.StoryFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 확정 전 피드백 (Backend#76). 확정과 함께 저장하고, 저장하지 못하면 확정도 하지 않는다.
 */
class ApproveFeedbackIntegrationTest extends IntegrationTest {

    @Autowired
    GenerationPoller poller;

    Browser parent;

    @BeforeEach
    void signUpParent() throws Exception {
        parent = signUp(newEmail());
    }

    @Test
    void 화면_문구로_보내도_코드로_저장하고_확정한_버전과_잇는다() throws Exception {
        long id = StoryFlow.reviewing(parent, poller);

        approve(id, """
                {"feedback": {"situationReflection": "잘 반영되었어요", "storySatisfaction": "보통이에요",
                 "readingIntent": "아직 읽어볼 생각이 없어요", "comment": ""}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        Map<String, Object> row = feedback(id);
        assertThat(row.get("situation_reflection")).isEqualTo("WELL");
        assertThat(row.get("story_satisfaction")).isEqualTo("NEUTRAL");
        assertThat(row.get("reading_intent")).isEqualTo("NOT_YET");
        assertThat(row.get("comment")).isNull();
        assertThat(row.get("version_id")).isEqualTo(currentVersionId(id));
    }

    @Test
    void 코드로_보내도_되고_의견은_앞뒤_공백을_지운다() throws Exception {
        long id = StoryFlow.reviewing(parent, poller);

        approve(id, """
                {"feedback": {"situationReflection": "PARTLY", "storySatisfaction": "SATISFIED",
                 "readingIntent": "DEFINITELY", "comment": "  그림이 좋아요  "}}""")
                .andExpect(status().isOk());

        Map<String, Object> row = feedback(id);
        assertThat(row.get("situation_reflection")).isEqualTo("PARTLY");
        assertThat(row.get("comment")).isEqualTo("그림이 좋아요");
    }

    @Test
    void 본문이_없거나_feedback이_없으면_확정만_한다() throws Exception {
        long noBody = StoryFlow.reviewing(parent, poller);
        long emptyBody = StoryFlow.reviewing(parent, poller);
        long nullFeedback = StoryFlow.reviewing(parent, poller);

        approve(noBody, null).andExpect(jsonPath("$.status").value("COMPLETED"));
        approve(emptyBody, "{}").andExpect(jsonPath("$.status").value("COMPLETED"));
        approve(nullFeedback, "{\"feedback\": null}").andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(feedbackCount(noBody) + feedbackCount(emptyBody) + feedbackCount(nullFeedback)).isZero();
    }

    @Test
    void 선택_답이_빠지면_400이고_확정하지_않는다() throws Exception {
        long id = StoryFlow.reviewing(parent, poller);

        approve(id, """
                {"feedback": {"situationReflection": "WELL", "readingIntent": "MAYBE", "comment": ""}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0].field").value("feedback.storySatisfaction"))
                .andExpect(jsonPath("$.fields[0].message").value("동화에 만족하는지 골라 주세요."));

        assertThat(storyStatus(id)).isEqualTo("REVIEWING");
        assertThat(feedbackCount(id)).isZero();
    }

    @Test
    void 고를_수_없는_값이면_400이고_확정하지_않는다() throws Exception {
        long id = StoryFlow.reviewing(parent, poller);

        approve(id, """
                {"feedback": {"situationReflection": "WELL", "storySatisfaction": "SATISFIED",
                 "readingIntent": "읽을래요", "comment": ""}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("feedback.readingIntent"))
                .andExpect(jsonPath("$.fields[0].message").value("고를 수 없는 값이에요."));

        assertThat(storyStatus(id)).isEqualTo("REVIEWING");
    }

    @Test
    void 의견이_500자를_넘으면_400이고_확정하지_않는다() throws Exception {
        long id = StoryFlow.reviewing(parent, poller);

        approve(id, """
                {"feedback": {"situationReflection": "WELL", "storySatisfaction": "SATISFIED",
                 "readingIntent": "DEFINITELY", "comment": "%s"}}""".formatted("가".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("feedback.comment"))
                .andExpect(jsonPath("$.fields[0].message").value("의견은 500자까지 쓸 수 있어요."));

        assertThat(storyStatus(id)).isEqualTo("REVIEWING");
        assertThat(feedbackCount(id)).isZero();
    }

    @Test
    void 다시_확정을_보내도_피드백은_하나다() throws Exception {
        long id = StoryFlow.reviewing(parent, poller);
        String body = """
                {"feedback": {"situationReflection": "WELL", "storySatisfaction": "SATISFIED",
                 "readingIntent": "DEFINITELY", "comment": ""}}""";

        approve(id, body).andExpect(status().isOk());
        approve(id, body).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        assertThat(feedbackCount(id)).isEqualTo(1);
    }

    private ResultActions approve(long id, String json) throws Exception {
        return parent.post(StoryFlow.url(id) + "/approve", json);
    }

    private Map<String, Object> feedback(long id) {
        return jdbc.queryForMap("SELECT * FROM story_feedbacks WHERE story_id = ?", id);
    }

    private int feedbackCount(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM story_feedbacks WHERE story_id = ?", Integer.class, id);
    }

    private String storyStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM stories WHERE id = ?", String.class, id);
    }

    private long currentVersionId(long id) {
        return jdbc.queryForObject("SELECT current_version_id FROM stories WHERE id = ?", Long.class, id);
    }
}
