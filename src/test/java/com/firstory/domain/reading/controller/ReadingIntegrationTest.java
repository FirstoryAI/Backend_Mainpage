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
import com.firstory.global.ai.FakeAiClient;
import com.firstory.support.Browser;
import com.firstory.support.Fixtures;
import com.firstory.support.IntegrationTest;
import com.firstory.support.StoryFlow;
import com.firstory.support.StoryStates;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
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
                .andExpect(jsonPath("$.title").value(FakeAiClient.SAMPLE.title(0)))
                .andExpect(jsonPath("$.beforeReading.intent").isNotEmpty())
                .andExpect(jsonPath("$.beforeReading.tips", hasSize(2)))
                .andExpect(jsonPath("$.prompts[*].seq", contains(1, 2)))
                .andExpect(jsonPath("$.prompts[*].afterPage", contains(4, 7)))
                .andExpect(jsonPath("$.prompts[0].id").isNumber())
                .andExpect(jsonPath("$.prompts[0].question").value("아로는 왜 혼자 앉았을까?"))
                .andExpect(jsonPath("$.prompts[0].purpose").value("감정 알아차리기"))
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
        assertThat(run.isRunning()).isFalse();
    }

    @Test
    void 읽기_흐름은_book_시작_쪽_넘김_대화_끝이고_서재에_함께_읽었어요로_보인다() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long versionId = currentVersionId(id);

        parent.get("/api/stories/" + id + "/book")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storyId").value(id))
                .andExpect(jsonPath("$.versionId").value(versionId))
                .andExpect(jsonPath("$.title").value(FakeAiClient.SAMPLE.title(0)))
                .andExpect(jsonPath("$.coverUrl").value("/api/stories/" + id + "/versions/" + versionId
                        + "/character-sheet"))
                .andExpect(jsonPath("$.pageCount").value(8))
                .andExpect(jsonPath("$.pages", hasSize(8)))
                .andExpect(jsonPath("$.pages[0].imageUrl").value("/api/stories/" + id + "/versions/" + versionId
                        + "/pages/1/image"))
                .andExpect(jsonPath("$.pages[0].imageStatus").value("DONE"))
                .andExpect(jsonPath("$.prompts[*].afterPage", contains(4, 7)))
                .andExpect(jsonPath("$.prompts[0].question").isNotEmpty())
                .andExpect(jsonPath("$.prompts[0].parentHint").isNotEmpty());

        String started = start(id).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastPage").value(0))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long sessionId = ((Number) JsonPath.read(started, "$.sessionId")).longValue();
        for (int page = 1; page <= 8; page++) {
            progress(sessionId, page).andExpect(status().isNoContent());
        }
        dialogue(sessionId, promptIds(versionId).getFirst(), "TALKED").andExpect(status().isNoContent());
        complete(sessionId).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT last_page FROM reading_sessions WHERE id = ?", Integer.class, sessionId))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM dialogue_actions WHERE session_id = ? AND action = 'TALKED'",
                Integer.class, sessionId)).isOne();
        parent.get("/api/stories?filter=READ").andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].label").value("함께 읽었어요"));
        parent.get("/api/stories/" + id).andExpect(jsonPath("$.readCount").value(1))
                .andExpect(jsonPath("$.lastReadAt").isNotEmpty());
    }

    @Test
    void 쪽_범위를_벗어나면_PAGE_OUT_OF_RANGE() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long sessionId = startSession(id);

        progress(sessionId, 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGE_OUT_OF_RANGE"));
        progress(sessionId, 9).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGE_OUT_OF_RANGE"));
        parent.put("/api/reading-sessions/" + sessionId + "/progress", "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 이어_읽기는_멈춘_세션과_쪽을_주고_끝나면_204() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        parent.get("/api/stories/" + id + "/reading-sessions/resume").andExpect(status().isNoContent());
        long sessionId = startSession(id);
        progress(sessionId, 5);

        parent.get("/api/stories/" + id + "/reading-sessions/resume")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.lastPage").value(5))
                .andExpect(jsonPath("$.startedAt").isNotEmpty());

        complete(sessionId);
        parent.get("/api/stories/" + id + "/reading-sessions/resume").andExpect(status().isNoContent());
        progress(sessionId, 6).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void 대화_버튼을_다시_누르면_마지막_값으로_바꾼다() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long sessionId = startSession(id);
        long promptId = promptIds(currentVersionId(id)).getFirst();

        dialogue(sessionId, promptId, "SKIPPED").andExpect(status().isNoContent());
        dialogue(sessionId, promptId, "TALKED").andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("SELECT action FROM dialogue_actions WHERE session_id = ?", String.class,
                sessionId)).containsExactly("TALKED");
        dialogue(sessionId, promptId, "MAYBE").andExpect(status().isBadRequest());
    }

    @Test
    void 다른_동화의_질문으로는_대화를_기록할_수_없다() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long other = StoryFlow.completed(parent, poller);
        long sessionId = startSession(id);

        dialogue(sessionId, promptIds(currentVersionId(other)).getFirst(), "TALKED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMPT_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialogue_actions WHERE session_id = ?", Integer.class,
                sessionId)).isZero();
    }

    @Test
    void 다른_회원의_세션은_쪽_넘김_대화_끝이_모두_404() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long sessionId = startSession(id);
        long promptId = promptIds(currentVersionId(id)).getFirst();
        Browser other = signUp(newEmail(), newPhone());

        other.put("/api/reading-sessions/" + sessionId + "/progress", "{\"page\": 2}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("READING_SESSION_NOT_FOUND"));
        other.put("/api/reading-sessions/" + sessionId + "/dialogue/" + promptId, "{\"action\": \"TALKED\"}")
                .andExpect(status().isNotFound());
        other.post("/api/reading-sessions/" + sessionId + "/complete", null).andExpect(status().isNotFound());
        other.get("/api/stories/" + id + "/book").andExpect(status().isNotFound());
        other.post("/api/stories/" + id + "/reading-sessions", null).andExpect(status().isNotFound());
        other.get("/api/stories/" + id + "/reading-sessions/resume").andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT last_page FROM reading_sessions WHERE id = ?", Integer.class,
                sessionId)).isZero();
        assertThat(jdbc.queryForObject("SELECT completed_at FROM reading_sessions WHERE id = ?", Object.class,
                sessionId)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialogue_actions WHERE session_id = ?", Integer.class,
                sessionId)).isZero();
    }

    @Test
    void 지운_동화의_세션은_404() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long sessionId = startSession(id);

        parent.delete("/api/stories/" + id).andExpect(status().isNoContent());

        progress(sessionId, 2).andExpect(status().isNotFound());
        complete(sessionId).andExpect(status().isNotFound());
    }

    @Test
    void 확정_전에는_읽을_수_없다() throws Exception {
        long id = StoryFlow.reviewing(parent, poller);

        parent.get("/api/stories/" + id + "/book").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        start(id).andExpect(status().isConflict());
        parent.get("/api/stories/" + id + "/reading-sessions/resume").andExpect(status().isConflict());
    }

    @Test
    void 두_번_끝내도_204이고_처음_읽은_시각은_그대로() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long sessionId = startSession(id);
        complete(sessionId).andExpect(status().isNoContent());
        Object firstRead = jdbc.queryForObject("SELECT first_read_completed_at FROM stories WHERE id = ?",
                Object.class, id);
        Object completedAt = jdbc.queryForObject("SELECT completed_at FROM reading_sessions WHERE id = ?",
                Object.class, sessionId);

        advance(Duration.ofMinutes(10));
        complete(sessionId).andExpect(status().isNoContent());
        // 다른 세션으로 다시 읽어도 처음 읽은 시각은 그대로
        long again = startSession(id);
        complete(again).andExpect(status().isNoContent());

        assertThat(firstRead).isNotNull();
        assertThat(jdbc.queryForObject("SELECT first_read_completed_at FROM stories WHERE id = ?", Object.class, id))
                .isEqualTo(firstRead);
        assertThat(jdbc.queryForObject("SELECT completed_at FROM reading_sessions WHERE id = ?", Object.class,
                sessionId)).isEqualTo(completedAt);
        parent.get("/api/stories/" + id).andExpect(jsonPath("$.readCount").value(2));
    }

    private ResultActions guide(long id) throws Exception {
        return parent.get("/api/stories/" + id + "/guide");
    }

    private ResultActions start(long id) throws Exception {
        return parent.post("/api/stories/" + id + "/reading-sessions", null);
    }

    private long startSession(long id) throws Exception {
        String body = start(id).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.sessionId")).longValue();
    }

    private ResultActions progress(long sessionId, int page) throws Exception {
        return parent.put("/api/reading-sessions/" + sessionId + "/progress", "{\"page\": " + page + "}");
    }

    private ResultActions dialogue(long sessionId, long promptId, String action) throws Exception {
        return parent.put("/api/reading-sessions/" + sessionId + "/dialogue/" + promptId,
                "{\"action\": \"" + action + "\"}");
    }

    private ResultActions complete(long sessionId) throws Exception {
        return parent.post("/api/reading-sessions/" + sessionId + "/complete", null);
    }

    private long currentVersionId(long id) {
        return jdbc.queryForObject("SELECT current_version_id FROM stories WHERE id = ?", Long.class, id);
    }

    private List<Long> promptIds(long versionId) {
        return jdbc.queryForList("""
                SELECT p.id FROM guide_prompts p JOIN reading_guides g ON g.id = p.guide_id
                WHERE g.version_id = ? ORDER BY p.seq""", Long.class, versionId);
    }
}
