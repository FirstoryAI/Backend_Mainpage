package com.firstory.domain.reading.controller;

import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.domain.generation.repository.GenerationRunRepository;
import com.firstory.domain.generation.repository.StoryVersionRepository;
import com.firstory.domain.generation.service.GenerationPoller;
import com.firstory.domain.generation.service.GenerationResultRecorder;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.repository.StoryInputRepository;
import com.firstory.domain.story.repository.StoryRepository;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.ai.AiGenerationResult;
import com.firstory.support.Browser;
import com.firstory.support.Fixtures;
import com.firstory.support.IntegrationTest;
import com.firstory.support.StoryFlow;
import com.firstory.support.StoryStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대화 가이드(GDE-01)와 함께 읽기(RD-01~03). 확정된 동화는 StoryFlow로 가입 → 만들기 → 생성 → 확정까지 만든다.
 */
class ReadingIntegrationTest extends IntegrationTest {

    @Autowired
    GenerationPoller poller;

    @Autowired
    GenerationResultRecorder recorder;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    UserRepository userRepository;

    @Autowired
    StoryRepository storyRepository;

    @Autowired
    StoryInputRepository storyInputRepository;

    @Autowired
    GenerationRunRepository runRepository;

    @Autowired
    StoryVersionRepository versionRepository;

    Browser parent;

    @BeforeEach
    void signUpParent() throws Exception {
        parent = signUp(newEmail(), newPhone());
    }

    @Test
    void 가이드는_확정된_동화의_지금_버전_가이드를_질문_순서대로_보여준다() throws Exception {
        long id = StoryFlow.completed(parent, poller);

        guide(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storyId").value(id))
                .andExpect(jsonPath("$.title").value("가짜 동화 1"))
                .andExpect(jsonPath("$.beforeReading.intent").isNotEmpty())
                .andExpect(jsonPath("$.beforeReading.tips", hasSize(2)))
                .andExpect(jsonPath("$.prompts[*].seq", contains(1, 2)))
                .andExpect(jsonPath("$.prompts[*].afterPage", contains(3, 6)))
                .andExpect(jsonPath("$.prompts[0].id").isNumber())
                .andExpect(jsonPath("$.prompts[0].question").value("아로는 지금 어떤 마음일까?"))
                .andExpect(jsonPath("$.prompts[0].purpose").value("emotion"))
                .andExpect(jsonPath("$.prompts[0].parentHint").isNotEmpty())
                .andExpect(jsonPath("$.afterReading.bridgeQuestion").isNotEmpty());
    }

    @Test
    void 확정_전에는_가이드를_볼_수_없다() throws Exception {
        long id = StoryFlow.reviewing(parent, poller);

        guide(id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void 다른_회원의_가이드는_404() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        Browser other = signUp(newEmail(), newPhone());

        other.get("/api/stories/" + id + "/guide").andExpect(status().isNotFound());
    }

    @Test
    void AI_가이드_형식_그대로_저장되고_parent_hint와_if_undecided가_없어도_된다() throws Exception {
        Long userId = userId(parent);
        StoryStates states = new StoryStates(tx, userRepository, storyRepository, storyInputRepository,
                runRepository, versionRepository);
        long id = states.create(userId, StoryStatus.GENERATING_IMAGES, false, clock.instant());
        long runId = tx.execute(status -> runRepository.save(Fixtures.run(storyRepository.getReferenceById(id), 1)))
                .getId();
        String prefix = "stories/" + id + "/runs/1/images/";
        List<AiGenerationResult.Page> pages = IntStream.rangeClosed(1, 8)
                .mapToObj(n -> new AiGenerationResult.Page(n, n + "쪽", null, prefix + "page-0" + n + ".png", "done"))
                .toList();
        AiGenerationResult result = new AiGenerationResult(1, "cp-1", "제목", null, List.of(), pages,
                new AiGenerationResult.Guide("의도", List.of("팁"), "다리 질문", null, List.of(
                        new AiGenerationResult.Prompt(5, "다섯째 쪽 질문", "action", null),
                        new AiGenerationResult.Prompt(2, "둘째 쪽 질문", "emotion", "힌트"))),
                null, null);

        recorder.record(runId, result);
        parent.post("/api/stories/" + id + "/approve", null).andExpect(status().isOk());

        // seq는 받은 순서대로 1부터 (쪽 순서로 다시 정렬하지 않는다)
        guide(id)
                .andExpect(jsonPath("$.beforeReading.intent").value("의도"))
                .andExpect(jsonPath("$.prompts[*].question", contains("다섯째 쪽 질문", "둘째 쪽 질문")))
                .andExpect(jsonPath("$.prompts[*].seq", contains(1, 2)))
                .andExpect(jsonPath("$.prompts[0].parentHint").value(nullValue()))
                .andExpect(jsonPath("$.afterReading.bridgeQuestion").value("다리 질문"))
                .andExpect(jsonPath("$.afterReading.ifUndecided").value(nullValue()));
        GenerationRun run = runRepository.findById(runId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(run.isRunning()).isFalse();
    }

    private ResultActions guide(long id) throws Exception {
        return parent.get("/api/stories/" + id + "/guide");
    }
}
