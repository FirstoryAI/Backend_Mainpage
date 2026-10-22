package com.firstory.domain.generation.controller;

import com.firstory.domain.generation.service.GenerationPoller;
import com.firstory.global.ai.FakeAiClient;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 생성 전 확인(CRT-06)부터 확정(REV-04)까지. AI는 FakeAiClient, 스케줄러는 꺼져 있어 pollOnce()를 직접 부른다.
 * FakeAiClient는 status를 부를 때마다 한 단계씩 나아가서 처음 만들기는 pollOnce 5번이면 REVIEWING이 된다.
 */
class GenerationIntegrationTest extends IntegrationTest {

    static final int POLLS_TO_REVIEW = 5;

    @Autowired
    FakeAiClient ai;

    @Autowired
    GenerationPoller poller;

    Browser parent;

    @BeforeEach
    void signUpParent() throws Exception {
        parent = signUp(newEmail(), newPhone());
    }

    @AfterEach
    void recoverAi() {
        ai.clearFailure();
    }

    @Test
    void 정상_생성은_pollOnce_5번이면_REVIEWING() throws Exception {
        long id = confirmingStory("용기를 주고 싶어요");

        confirm(id)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.storyId").value(id))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.runNo").value(1));
        pollUntilReview();

        assertThat(storyStatus(id)).isEqualTo("REVIEWING");
        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM generation_runs WHERE story_id = ?", id);
        assertThat(run.get("status")).isEqualTo("SUCCEEDED");
        assertThat(run.get("trigger_type")).isEqualTo("INITIAL");
        assertThat(run.get("input_tokens")).isEqualTo(FakeAiClient.INPUT_TOKENS);
        assertThat(run.get("output_tokens")).isEqualTo(FakeAiClient.OUTPUT_TOKENS);
        assertThat(run.get("image_count")).isEqualTo(9);
        assertThat(run.get("finished_at")).isNotNull();
        Map<String, Object> version = jdbc.queryForMap("SELECT * FROM story_versions WHERE story_id = ?", id);
        assertThat(version.get("version_no")).isEqualTo(1);
        assertThat(version.get("parent_version_id")).isNull();
        assertThat(version.get("title")).isEqualTo("가짜 동화 1");
        assertThat(version.get("checkpoint_id")).isEqualTo("fake-cp-1");
        assertThat(jdbc.queryForObject("SELECT current_version_id FROM stories WHERE id = ?", Long.class, id))
                .isEqualTo(((Number) version.get("id")).longValue());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM story_pages WHERE version_id = ? AND image_status = 'DONE'",
                Integer.class, version.get("id"))).isEqualTo(8);
        assertThat(jdbc.queryForList("""
                SELECT p.after_page FROM guide_prompts p JOIN reading_guides g ON g.id = p.guide_id
                WHERE g.version_id = ? ORDER BY p.seq""", Integer.class, version.get("id"))).containsExactly(3, 6);
    }

    void pollUntilReview() {
        for (int i = 0; i < POLLS_TO_REVIEW; i++) {
            poller.pollOnce();
        }
    }

    /**
     * 입력과 인터뷰를 마친 CONFIRMING 동화.
     */
    long confirmingStory(String message) throws Exception {
        String body = parent.post("/api/stories", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.storyId")).longValue();
        parent.put(storyUrl(id) + "/situation", """
                {"timing": "PAST", "category": "FRIEND_CONFLICT", "description": "친구가 밀쳤어요",
                 "reactions": ["CRIED"], "emotions": ["SAD"]}""").andExpect(jsonPath("$.saved").value(true));
        parent.put(storyUrl(id) + "/child", """
                {"childId": null, "saveForNextTime": false,
                 "profile": {"birthYm": "2021-03", "gender": "FEMALE", "traits": ["SHY"], "interestCodes": ["DINOSAUR"]}}""")
                .andExpect(jsonPath("$.saved").value(true));
        parent.put(storyUrl(id) + "/message", """
                {"message": "%s"}""".formatted(message)).andExpect(jsonPath("$.saved").value(true));
        parent.post(storyUrl(id) + "/interview/start", null).andExpect(status().isOk());
        parent.post(storyUrl(id) + "/interview/answer", """
                {"skip": true}""").andExpect(jsonPath("$.status").value("CONFIRMING"));
        return id;
    }

    ResultActions confirm(long id) throws Exception {
        return parent.post(storyUrl(id) + "/confirm", null);
    }

    static String storyUrl(long id) {
        return "/api/stories/" + id;
    }

    String storyStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM stories WHERE id = ?", String.class, id);
    }
}
