package com.firstory.domain.generation.controller;

import com.firstory.domain.generation.entity.RegenerateScope;
import com.firstory.domain.generation.service.GenerationPoller;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.firstory.support.StoryFlow;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미리보기에서 옮긴 의성어·의태어 조각 자리 (#100). 가짜 AI는 2쪽에 "콩닥콩닥"(두 조각 30·45, 40), 4쪽은 의성어가 없다.
 */
class SoundWordPositionsIntegrationTest extends IntegrationTest {

    @Autowired
    GenerationPoller poller;

    @Test
    void 옮긴_조각만_바뀌고_미리보기와_확정_뒤_읽기에_그대로_나온다() throws Exception {
        Browser parent = signUp(newEmail());
        long id = StoryFlow.reviewing(parent, poller);
        long versionId = currentVersionId(parent, id);

        parent.put(positions(id, versionId, 2), """
                {"pieces": [{"index": 1, "x": 57.2, "y": 35.0}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("콩닥콩닥"))
                .andExpect(jsonPath("$.pieces[0].x").value(30.0))
                .andExpect(jsonPath("$.pieces[1].x").value(57.2))
                .andExpect(jsonPath("$.pieces[1].y").value(35.0))
                .andExpect(jsonPath("$.pieces[1].text").value("콩닥"))
                .andExpect(jsonPath("$.pieces[1].side").value("RIGHT"));

        parent.get(StoryFlow.url(id) + "/preview")
                .andExpect(jsonPath("$.pages[1].soundWord.pieces[0].x").value(30.0))
                .andExpect(jsonPath("$.pages[1].soundWord.pieces[1].x").value(57.2));

        parent.post(StoryFlow.url(id) + "/approve", null).andExpect(status().isOk());
        parent.get(StoryFlow.url(id) + "/book")
                .andExpect(jsonPath("$.pages[1].soundWord.pieces[1].x").value(57.2))
                .andExpect(jsonPath("$.pages[1].soundWord.pieces[1].y").value(35.0));

        // 확정 뒤에는 옮길 수 없다
        parent.put(positions(id, versionId, 2), """
                {"pieces": [{"index": 0, "x": 10, "y": 10}]}""")
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void 버전마다_따로_남는다() throws Exception {
        Browser parent = signUp(newEmail());
        long id = StoryFlow.reviewing(parent, poller);
        long first = currentVersionId(parent, id);
        parent.put(positions(id, first, 2), """
                {"pieces": [{"index": 0, "x": 12.5, "y": 60}]}""").andExpect(status().isOk());

        parent.post(StoryFlow.url(id) + "/regenerate", """
                {"scope": "%s"}""".formatted(RegenerateScope.TEXT)).andExpect(status().isAccepted());
        for (int i = 0; i < 4; i++) {
            poller.pollOnce();
        }
        long second = currentVersionId(parent, id);
        parent.get(StoryFlow.url(id) + "/preview")
                .andExpect(jsonPath("$.pages[1].soundWord.pieces[0].x").value(30.0));

        parent.put(StoryFlow.url(id) + "/current-version", """
                {"versionId": %d}""".formatted(first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pages[1].soundWord.pieces[0].x").value(12.5));
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void 잘못된_쪽_조각_좌표는_막는다() throws Exception {
        Browser parent = signUp(newEmail());
        long id = StoryFlow.reviewing(parent, poller);
        long versionId = currentVersionId(parent, id);

        parent.put(positions(id, versionId, 4), """
                {"pieces": [{"index": 0, "x": 10, "y": 10}]}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SOUND_WORD_NOT_FOUND"));
        parent.put(positions(id, versionId, 11), """
                {"pieces": [{"index": 0, "x": 10, "y": 10}]}""")
                .andExpect(jsonPath("$.code").value("PAGE_OUT_OF_RANGE"));
        parent.put(positions(id, versionId, 2), """
                {"pieces": [{"index": 2, "x": 10, "y": 10}]}""")
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        parent.put(positions(id, versionId, 2), """
                {"pieces": [{"index": 0, "x": 10, "y": 10}, {"index": 0, "x": 20, "y": 20}]}""")
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        parent.put(positions(id, versionId, 2), """
                {"pieces": [{"index": 0, "x": 100.1, "y": 10}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        parent.put(positions(id, versionId, 2), """
                {"pieces": []}""")
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        parent.put(positions(id, 999_999L, 2), """
                {"pieces": [{"index": 0, "x": 10, "y": 10}]}""")
                .andExpect(jsonPath("$.code").value("VERSION_NOT_FOUND"));

        parent.get(StoryFlow.url(id) + "/preview")
                .andExpect(jsonPath("$.pages[1].soundWord.pieces[0].x").value(30.0));
    }

    @Test
    void 다른_회원의_동화는_찾을_수_없다() throws Exception {
        Browser owner = signUp(newEmail());
        long id = StoryFlow.reviewing(owner, poller);
        long versionId = currentVersionId(owner, id);

        signUp(newEmail()).put(positions(id, versionId, 2), """
                {"pieces": [{"index": 0, "x": 10, "y": 10}]}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORY_NOT_FOUND"));
    }

    private long currentVersionId(Browser browser, long id) throws Exception {
        String body = browser.get(StoryFlow.url(id) + "/preview").andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.version.id")).longValue();
    }

    private static String positions(long id, long versionId, int pageNo) {
        return StoryFlow.url(id) + "/versions/" + versionId + "/pages/" + pageNo + "/sound-word-positions";
    }
}
