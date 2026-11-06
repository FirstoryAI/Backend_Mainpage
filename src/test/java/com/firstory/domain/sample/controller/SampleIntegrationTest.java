package com.firstory.domain.sample.controller;

import com.firstory.domain.generation.service.GenerationPoller;
import com.firstory.domain.sample.service.SampleCatalog;
import com.firstory.global.ai.FakeAiClient;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.firstory.support.StoryFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 홈 샘플 카드의 미리 만들어 둔 동화 (로그인 없이). 카드와 동화의 연결(SAMPLE_STORIES)은 SampleCatalog를 바꿔 끼운다.
 */
class SampleIntegrationTest extends IntegrationTest {

    @Autowired
    GenerationPoller poller;

    @MockitoBean
    SampleCatalog catalog;

    Browser parent;
    Browser guest;

    @BeforeEach
    void setUp() throws Exception {
        given(catalog.storyId(anyString())).willReturn(Optional.empty());
        parent = signUp(newEmail());
        guest = browser();
    }

    @Test
    void 연결한_확정_동화는_로그인_없이_대화_가이드_없이_읽는다() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        given(catalog.storyId("friend")).willReturn(Optional.of(id));

        guest.get("/api/samples/friend")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value("friend"))
                .andExpect(jsonPath("$.title").value(FakeAiClient.SAMPLE.title(0)))
                .andExpect(jsonPath("$.coverUrl").value("/api/samples/friend/cover"))
                .andExpect(jsonPath("$.pageCount").value(10))
                .andExpect(jsonPath("$.pages", hasSize(10)))
                .andExpect(jsonPath("$.pages[0].pageNo").value(1))
                .andExpect(jsonPath("$.pages[0].text").isNotEmpty())
                .andExpect(jsonPath("$.pages[0].imageUrl").value("/api/samples/friend/pages/1/image"))
                .andExpect(jsonPath("$.pages[0].imageStatus").value("DONE"))
                .andExpect(jsonPath("$.pages[0].textBlocks").isArray())
                .andExpect(jsonPath("$.prompts").doesNotExist())
                .andExpect(jsonPath("$.storyId").doesNotExist())
                .andExpect(jsonPath("$.versionId").doesNotExist());
    }

    @Test
    void 표지와_쪽_그림은_로그인_없이_공용_캐시로_내린다() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        given(catalog.storyId("friend")).willReturn(Optional.of(id));

        for (String url : new String[]{"/api/samples/friend/cover", "/api/samples/friend/pages/3/image"}) {
            guest.get(url)
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=3600, public"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"));
        }
    }

    @Test
    void 없는_쪽_그림은_IMAGE_NOT_FOUND() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        given(catalog.storyId("friend")).willReturn(Optional.of(id));

        guest.get("/api/samples/friend/pages/11/image")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    void 연결이_없는_카드는_SAMPLE_NOT_FOUND() throws Exception {
        for (String url : new String[]{"/api/samples/nothing", "/api/samples/nothing/cover",
                "/api/samples/nothing/pages/1/image"}) {
            guest.get(url).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SAMPLE_NOT_FOUND"));
        }
    }

    @Test
    void 확정_전이거나_지운_동화를_연결했으면_SAMPLE_NOT_FOUND() throws Exception {
        long reviewing = StoryFlow.reviewing(parent, poller);
        long deleted = StoryFlow.completed(parent, poller);
        parent.delete("/api/stories/" + deleted).andExpect(status().isNoContent());
        given(catalog.storyId("emotion")).willReturn(Optional.of(reviewing));
        given(catalog.storyId("tantrum")).willReturn(Optional.of(deleted));
        given(catalog.storyId("missing")).willReturn(Optional.of(Long.MAX_VALUE));

        for (String card : new String[]{"emotion", "tantrum", "missing"}) {
            guest.get("/api/samples/" + card)
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SAMPLE_NOT_FOUND"));
            guest.get("/api/samples/" + card + "/cover")
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SAMPLE_NOT_FOUND"));
        }
    }
}
