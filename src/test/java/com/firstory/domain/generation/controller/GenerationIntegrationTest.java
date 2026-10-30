package com.firstory.domain.generation.controller;

import com.firstory.domain.generation.entity.RegenerateScope;
import com.firstory.domain.generation.service.GenerationPoller;
import com.firstory.domain.notification.entity.NotificationType;
import com.firstory.domain.notification.service.NotificationService;
import com.firstory.global.ai.FakeAiClient;
import com.firstory.global.error.ErrorCode;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 생성 전 확인(CRT-06)부터 확정(REV-04)까지. AI는 FakeAiClient, 스케줄러는 꺼져 있어 pollOnce()를 직접 부른다.
 * FakeAiClient는 status를 부를 때마다 한 단계씩 나아간다. 처음 만들기와 다시 시도는 pollOnce 5번,
 * 글 다시(TEXT)는 4번, 그림만 다시(IMAGES)는 3번이면 REVIEWING이 된다.
 */
class GenerationIntegrationTest extends IntegrationTest {

    static final int POLLS_TO_REVIEW = 5;
    static final int POLLS_TO_REVIEW_TEXT = 4;
    static final int POLLS_TO_REVIEW_IMAGES = 3;
    static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47};
    static final String MESSAGE = "용기를 주고 싶어요";

    @Autowired
    FakeAiClient ai;

    @Autowired
    GenerationPoller poller;

    @Autowired
    NotificationService notificationService;

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
        long id = confirmingStory(MESSAGE);

        confirm(id)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.storyId").value(id))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.runNo").value(1))
                .andExpect(jsonPath("$.stage").value(nullValue()));
        pollTimes(POLLS_TO_REVIEW);

        assertThat(storyStatus(id)).isEqualTo("REVIEWING");
        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM generation_runs WHERE story_id = ?", id);
        assertThat(run.get("status")).isEqualTo("SUCCEEDED");
        assertThat(run.get("trigger_type")).isEqualTo("INITIAL");
        assertThat(run.get("scope")).isEqualTo("ALL");
        assertThat(run.get("input_tokens")).isEqualTo(FakeAiClient.INPUT_TOKENS);
        assertThat(run.get("output_tokens")).isEqualTo(FakeAiClient.OUTPUT_TOKENS);
        assertThat(run.get("image_count")).isEqualTo(12);   // 쪽 10 + 시트 + 표지
        assertThat(run.get("langsmith_trace_id")).isNotNull();
        assertThat(run.get("finished_at")).isNotNull();
        Map<String, Object> version = jdbc.queryForMap("SELECT * FROM story_versions WHERE story_id = ?", id);
        assertThat(version.get("version_no")).isEqualTo(1);
        assertThat(version.get("parent_version_id")).isNull();
        assertThat(version.get("title")).isEqualTo(FakeAiClient.SAMPLE.title(0));
        assertThat(version.get("checkpoint_id")).isEqualTo("fake-cp-1");
        assertThat(currentVersionId(id)).isEqualTo(((Number) version.get("id")).longValue());
        assertThat(jdbc.queryForObject("SELECT title FROM stories WHERE id = ?", String.class, id))
                .isEqualTo(FakeAiClient.SAMPLE.title(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM story_pages WHERE version_id = ? AND image_status = 'DONE'",
                Integer.class, version.get("id"))).isEqualTo(10);
        assertThat(jdbc.queryForList("""
                SELECT p.after_page FROM guide_prompts p JOIN reading_guides g ON g.id = p.guide_id
                WHERE g.version_id = ? ORDER BY p.seq""", Integer.class, version.get("id"))).containsExactly(4, 8);
    }

    @Test
    void 진행_상태는_pollOnce마다_단계가_나아간다() throws Exception {
        long id = confirmingStory(MESSAGE);
        confirm(id);

        expectProgress(id, 1, "이야기 짜기", "GENERATING_TEXT");
        expectProgress(id, 2, "글 쓰기", "GENERATING_TEXT");
        expectProgress(id, 3, "그림 그리기", "GENERATING_IMAGES");
        expectProgress(id, 4, "책으로 묶기", "GENERATING_IMAGES");
        poller.pollOnce();
        generation(id).andExpect(jsonPath("$.status").value("REVIEWING"))
                .andExpect(jsonPath("$.error").value(nullValue()));
    }

    @Test
    void 진행_조회는_AI를_부르지_않는다() throws Exception {
        long id = confirmingStory(MESSAGE);
        confirm(id);
        poller.pollOnce();
        int calls = ai.statusCalls(threadId(id));

        for (int i = 0; i < 5; i++) {
            generation(id).andExpect(status().isOk()).andExpect(jsonPath("$.stage").value(1));
        }

        assertThat(ai.statusCalls(threadId(id))).isEqualTo(calls);
    }

    @Test
    void 민감_주제인데_분위기를_고르지_않으면_TONE_REQUIRED() throws Exception {
        long id = confirmingStory("할머니가 돌아가셨어요 [[SENSITIVE]]", MESSAGE);

        assertError(confirm(id), ErrorCode.TONE_REQUIRED);
        assertThat(storyStatus(id)).isEqualTo("CONFIRMING");

        parent.put(storyUrl(id) + "/tone", """
                {"tone": "WARM"}""").andExpect(status().isOk());
        confirm(id).andExpect(status().isAccepted());
    }

    @Test
    void CONFIRMING이_아니면_만들기를_시작할_수_없다() throws Exception {
        long id = reviewingStory();

        assertError(confirm(id), ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void 미리보기는_10쪽이고_주소에_파일_key나_경로가_없다() throws Exception {
        long id = reviewingStory();
        long versionId = currentVersionId(id);

        String body = parent.get(storyUrl(id) + "/preview")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storyId").value(id))
                .andExpect(jsonPath("$.status").value("REVIEWING"))
                .andExpect(jsonPath("$.version.id").value(versionId))
                .andExpect(jsonPath("$.version.versionNo").value(1))
                .andExpect(jsonPath("$.version.title").value(FakeAiClient.SAMPLE.title(0)))
                .andExpect(jsonPath("$.characterSheetUrl")
                        .value(storyUrl(id) + "/versions/" + versionId + "/character-sheet"))
                .andExpect(jsonPath("$.coverUrl").value(storyUrl(id) + "/versions/" + versionId + "/cover"))
                .andExpect(jsonPath("$.pages", hasSize(10)))
                .andExpect(jsonPath("$.pages[0].text").value(FakeAiClient.SAMPLE.pageText(1)))
                .andExpect(jsonPath("$.pages[0].textPosition").value("BOTTOM"))
                .andExpect(jsonPath("$.pages[2].textPosition").value("TOP"))   // 가짜 AI는 3의 배수 쪽만 위
                .andExpect(jsonPath("$.pages[*].imageStatus", everyItem(org.hamcrest.Matchers.is("DONE"))))
                .andExpect(jsonPath("$.pages[*].imageUrl", everyItem(matchesPattern(
                        "/api/stories/" + id + "/versions/" + versionId + "/pages/\\d+/image"))))
                .andExpect(jsonPath("$.regenerateRemaining").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("stories/" + id + "/runs").doesNotContain("page-01.png")
                .doesNotContain("test-storage");
    }

    @Test
    void 생성_중에는_미리보기를_볼_수_없다() throws Exception {
        long id = confirmingStory(MESSAGE);
        confirm(id);

        assertError(parent.get(storyUrl(id) + "/preview"), ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void 그림_API는_PNG와_보안_헤더를_내려준다() throws Exception {
        long id = reviewingStory();
        long versionId = currentVersionId(id);

        MvcResult result = parent.get(imageUrl(id, versionId, 1))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=3600, private"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"))
                .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).startsWith(PNG_SIGNATURE);

        parent.get(storyUrl(id) + "/versions/" + versionId + "/character-sheet")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"));

        // 표지는 진짜 AI처럼 정사각형
        byte[] cover = parent.get(storyUrl(id) + "/versions/" + versionId + "/cover")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage coverImage = ImageIO.read(new ByteArrayInputStream(cover));
        assertThat(coverImage.getWidth()).isEqualTo(1024);
        assertThat(coverImage.getHeight()).isEqualTo(1024);
    }

    @Test
    void 그림_API는_내_동화의_버전과_쪽이_아니면_404() throws Exception {
        long id = reviewingStory();
        long versionId = currentVersionId(id);
        long otherStory = reviewingStory();
        long otherVersionId = currentVersionId(otherStory);

        Browser other = signUp(newEmail(), newPhone());
        assertError(other.get(imageUrl(id, versionId, 1)), ErrorCode.STORY_NOT_FOUND);
        // 다른 동화의 버전 id를 이 동화 주소에 넣어도 404
        assertError(parent.get(imageUrl(id, otherVersionId, 1)), ErrorCode.VERSION_NOT_FOUND);
        assertError(parent.get(imageUrl(id, versionId, 11)), ErrorCode.IMAGE_NOT_FOUND);
        assertError(parent.get(imageUrl(id, versionId, 0)), ErrorCode.IMAGE_NOT_FOUND);
        browser().get(imageUrl(id, versionId, 1)).andExpect(status().isUnauthorized());
    }

    @Test
    void 그림이_실패한_쪽은_주소가_없고_그림_API도_404() throws Exception {
        long id = reviewingStory();
        long versionId = currentVersionId(id);
        jdbc.update("UPDATE story_pages SET image_status = 'FAILED' WHERE version_id = ? AND page_no = 2", versionId);

        parent.get(storyUrl(id) + "/preview")
                .andExpect(jsonPath("$.pages[1].imageStatus").value("FAILED"))
                .andExpect(jsonPath("$.pages[1].imageUrl").value(nullValue()));
        assertError(parent.get(imageUrl(id, versionId, 2)), ErrorCode.IMAGE_NOT_FOUND);
    }

    @Test
    void 그림만_다시는_고른_쪽만_새_key이고_나머지는_버전_1의_key() throws Exception {
        long id = reviewingStory();
        long v1 = currentVersionId(id);

        regenerate(id, """
                {"scope": "IMAGES", "pages": [3]}""")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.safety").value(nullValue()));
        pollTimes(POLLS_TO_REVIEW_IMAGES);

        long v2 = currentVersionId(id);
        assertThat(v2).isNotEqualTo(v1);
        List<String> before = imageKeys(v1);
        List<String> after = imageKeys(v2);
        for (int i = 0; i < 8; i++) {
            if (i == 2) {
                assertThat(after.get(i)).isNotEqualTo(before.get(i)).contains("/runs/2/");
            } else {
                assertThat(after.get(i)).isEqualTo(before.get(i));
            }
        }
        // 일부 쪽 그림만 다시는 표지도 버전 1 것
        assertThat(coverKey(v2)).isEqualTo(coverKey(v1)).contains("/runs/1/images/cover.png");
        Map<String, Object> run = jdbc.queryForMap("SELECT * FROM generation_runs WHERE story_id = ? AND run_no = 2", id);
        assertThat(run.get("trigger_type")).isEqualTo("REGENERATE");
        assertThat(run.get("scope")).isEqualTo("IMAGES");
        assertThat(run.get("target_pages").toString()).isEqualTo("[3]");
        assertThat(((Number) run.get("base_version_id")).longValue()).isEqualTo(v1);
        parent.get(storyUrl(id) + "/preview").andExpect(jsonPath("$.regenerateRemaining").value(0))
                .andExpect(jsonPath("$.version.versionNo").value(2));
        parent.get(storyUrl(id) + "/versions")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNo").value(1))
                .andExpect(jsonPath("$[0].parentVersionNo").value(nullValue()))
                .andExpect(jsonPath("$[0].scope").value("ALL"))
                .andExpect(jsonPath("$[0].current").value(false))
                .andExpect(jsonPath("$[1].parentVersionNo").value(1))
                .andExpect(jsonPath("$[1].scope").value("IMAGES"))
                .andExpect(jsonPath("$[1].current").value(true));
        // 이전 쪽 그림도 새 버전 주소로 내려준다
        parent.get(imageUrl(id, v2, 1)).andExpect(status().isOk());
    }

    @Test
    void 다시_그릴_쪽과_범위를_검사한다() throws Exception {
        long id = reviewingStory();

        assertError(regenerate(id, """
                {"scope": "IMAGES", "pages": [0]}"""), ErrorCode.INVALID_PAGE_SELECTION);
        assertError(regenerate(id, """
                {"scope": "IMAGES", "pages": [11]}"""), ErrorCode.INVALID_PAGE_SELECTION);
        assertError(regenerate(id, """
                {"scope": "IMAGES", "pages": [3, 3]}"""), ErrorCode.INVALID_PAGE_SELECTION);
        assertError(regenerate(id, """
                {"scope": "TEXT", "pages": [3]}"""), ErrorCode.INVALID_PAGE_SELECTION);
        assertError(regenerate(id, """
                {"scope": "ALL"}"""), ErrorCode.VALIDATION_FAILED);
        assertError(regenerate(id, """
                {"scope": "TEXT", "feedback": "%s"}""".formatted("가".repeat(301))), ErrorCode.FEEDBACK_TOO_LONG);
        assertThat(regenerateCount(id)).isZero();
    }

    @Test
    void 다시_만들기_요청이_AI에서_실패하면_실행도_횟수도_저장하지_않는다() throws Exception {
        long id = reviewingStory();
        ai.failWith(ErrorCode.AI_UNAVAILABLE);

        assertError(regenerate(id, """
                {"scope": "TEXT"}"""), ErrorCode.AI_UNAVAILABLE);

        assertThat(regenerateCount(id)).isZero();
        assertThat(runs(id)).isOne();
        assertThat(storyStatus(id)).isEqualTo("REVIEWING");
    }

    @Test
    void 다시_만들기는_1번까지() throws Exception {
        long id = reviewingStory();
        regenerate(id, """
                {"scope": "IMAGES", "pages": [1]}""").andExpect(status().isAccepted());
        pollTimes(POLLS_TO_REVIEW_IMAGES);
        assertThat(storyStatus(id)).isEqualTo("REVIEWING");

        assertError(regenerate(id, """
                {"scope": "IMAGES", "pages": [1]}"""), ErrorCode.REGENERATION_LIMIT_REACHED);
        assertThat(regenerateCount(id)).isOne();
        parent.get(storyUrl(id) + "/preview").andExpect(jsonPath("$.regenerateRemaining").value(0));
    }

    @Test
    void 메모가_REVISE면_다시_만들지_않고_두_번째는_차단() throws Exception {
        long id = reviewingStory();

        regenerate(id, """
                {"scope": "TEXT", "feedback": "혼내는 이야기로 [[REVISE]]"}""")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.status").value("REVIEWING"))
                .andExpect(jsonPath("$.safety.action").value("ASK_REVISE"));
        assertThat(regenerateCount(id)).isZero();
        assertThat(runs(id)).isOne();
        assertThat(jdbc.queryForObject("SELECT source FROM safety_flags WHERE story_id = ?", String.class, id))
                .isEqualTo("REGEN_FEEDBACK");

        regenerate(id, """
                {"scope": "TEXT", "feedback": "또 [[REVISE]]"}""")
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.safety.helpKind").value("PARENTING"));
        assertThat(storyStatus(id)).isEqualTo("BLOCKED");
        assertError(parent.post(storyUrl(id) + "/approve", null), ErrorCode.STORY_BLOCKED);
    }

    @Test
    void 버전_나무는_되돌린_버전에서_갈라진다() throws Exception {
        long id = reviewingStory();
        long v1 = currentVersionId(id);
        regenerate(id, """
                {"scope": "TEXT", "feedback": "더 밝게"}""").andExpect(jsonPath("$.status").value("REQUESTED"));
        pollTimes(POLLS_TO_REVIEW_TEXT);
        long v2 = currentVersionId(id);

        parent.put(storyUrl(id) + "/current-version", """
                {"versionId": %d}""".formatted(v1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version.versionNo").value(1));
        // 다시 만들기는 1회지만(firstory.policy.max-regenerations), 횟수를 늘렸을 때의 버전 나무를 보려고 되돌린다
        jdbc.update("UPDATE stories SET regenerate_count = 0 WHERE id = ?", id);
        regenerate(id, """
                {"scope": "IMAGES", "pages": [2]}""").andExpect(status().isAccepted());
        pollTimes(POLLS_TO_REVIEW_IMAGES);
        long v3 = currentVersionId(id);

        assertThat(ai.lastBaseCheckpointId(threadId(id))).isEqualTo(FakeAiClient.checkpointId(1));
        assertThat(parentVersionId(v3)).isEqualTo(v1);
        assertThat(parentVersionId(v2)).isEqualTo(v1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM story_versions WHERE story_id = ?", Integer.class, id))
                .isEqualTo(3);
        // 그림만 다시는 v1의 글을 쓴다 (v2의 글이 아님)
        parent.get(storyUrl(id) + "/preview").andExpect(jsonPath("$.pages[0].text").value(FakeAiClient.SAMPLE.pageText(1)));
    }

    @Test
    void 버전_전환은_횟수를_바꾸지_않고_AI를_부르지_않는다() throws Exception {
        long id = reviewingStory();
        long v1 = currentVersionId(id);
        regenerate(id, """
                {"scope": "IMAGES"}""");
        pollTimes(POLLS_TO_REVIEW_IMAGES);
        long v2 = currentVersionId(id);
        int statusCalls = ai.statusCalls(threadId(id));

        parent.put(storyUrl(id) + "/current-version", """
                {"versionId": %d}""".formatted(v1)).andExpect(status().isOk());
        parent.put(storyUrl(id) + "/current-version", """
                {"versionId": %d}""".formatted(v2)).andExpect(status().isOk());
        parent.put(storyUrl(id) + "/current-version", """
                {"versionId": %d}""".formatted(v1)).andExpect(status().isOk());

        assertThat(currentVersionId(id)).isEqualTo(v1);
        assertThat(regenerateCount(id)).isOne();
        assertThat(ai.statusCalls(threadId(id))).isEqualTo(statusCalls);
        assertThat(runs(id)).isEqualTo(2);
    }

    @Test
    void 다른_동화의_버전으로는_바꿀_수_없다() throws Exception {
        long id = reviewingStory();
        long other = reviewingStory();

        assertError(parent.put(storyUrl(id) + "/current-version", """
                {"versionId": %d}""".formatted(currentVersionId(other))), ErrorCode.VERSION_NOT_FOUND);
    }

    @Test
    void 확정하면_COMPLETED이고_알림을_보내지_않는다() throws Exception {
        Browser smsParent = signUpWithSms();
        parent = smsParent;
        long id = reviewingStory();
        int notifications = notifications(id);

        parent.post(storyUrl(id) + "/approve", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());

        assertThat(storyStatus(id)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT confirmed_at FROM stories WHERE id = ?", Object.class, id)).isNotNull();
        assertThat(notifications(id)).isEqualTo(notifications);
        parent.get(storyUrl(id) + "/preview").andExpect(status().isOk());
        assertError(regenerate(id, """
                {"scope": "TEXT"}"""), ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void AI_확정_정리가_실패해도_확정은_유지한다() throws Exception {
        long id = reviewingStory();
        ai.failWith(ErrorCode.AI_UNAVAILABLE);

        parent.post(storyUrl(id) + "/approve", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(storyStatus(id)).isEqualTo("COMPLETED");
    }

    @Test
    void 실패하면_알림을_보내고_다시_시도하면_횟수_없이_성공한다() throws Exception {
        String phone = newPhone();
        parent = signUpWithSms(phone);
        long id = confirmingStory("용기 " + FakeAiClient.FAIL_MARKER);
        confirm(id);

        pollTimes(2);

        assertThat(storyStatus(id)).isEqualTo("FAILED");
        generation(id).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value(FakeAiClient.FAILURE_CODE));
        assertThat(notificationTypes(id)).containsExactlyInAnyOrder("STORY_STARTED", "STORY_FAILED");
        assertThat(sms.lastText(phone)).contains("만들지 못했어요");
        // 결과 위기 신호가 아닌 실패는 retry
        int regenerateCalls = ai.regenerateCalls(threadId(id));

        parent.post(storyUrl(id) + "/retry", null)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.runNo").value(2));
        assertThat(ai.retryCalls(threadId(id))).isOne();
        assertThat(ai.regenerateCalls(threadId(id))).isEqualTo(regenerateCalls);
        pollTimes(POLLS_TO_REVIEW);

        assertThat(storyStatus(id)).isEqualTo("REVIEWING");
        assertThat(regenerateCount(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT trigger_type FROM generation_runs WHERE story_id = ? AND run_no = 2",
                String.class, id)).isEqualTo("RETRY");
        // 다시 시도는 시작 알림 없이 완료 알림만
        assertThat(notificationTypes(id)).containsExactlyInAnyOrder("STORY_STARTED", "STORY_FAILED",
                "STORY_COMPLETED");
    }

    @Test
    void 실패한_동화가_아니면_다시_시도할_수_없다() throws Exception {
        long id = reviewingStory();

        assertError(parent.post(storyUrl(id) + "/retry", null), ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void 시간이_지나도_RUNNING이면_TIMEOUT으로_실패() throws Exception {
        long id = confirmingStory(MESSAGE);
        confirm(id);
        poller.pollOnce();
        int calls = ai.statusCalls(threadId(id));

        advance(Duration.ofMinutes(16));
        poller.pollOnce();

        assertThat(storyStatus(id)).isEqualTo("FAILED");
        generation(id).andExpect(jsonPath("$.error").value("TIMEOUT"));
        assertThat(ai.statusCalls(threadId(id))).as("시간이 지났으면 AI에 묻지 않는다").isEqualTo(calls);
    }

    @Test
    void AI에_연결하지_못하면_아무것도_바꾸지_않는다() throws Exception {
        long id = confirmingStory(MESSAGE);
        confirm(id);
        ai.failWith(ErrorCode.AI_UNAVAILABLE);

        poller.pollOnce();

        assertThat(storyStatus(id)).isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("SELECT status FROM generation_runs WHERE story_id = ?", String.class, id))
                .isEqualTo("RUNNING");
        ai.clearFailure();
        pollTimes(POLLS_TO_REVIEW);
        assertThat(storyStatus(id)).isEqualTo("REVIEWING");
    }

    @Test
    void 결과에_위기_신호가_있으면_실패하고_부모_REVISE_횟수에_넣지_않는다() throws Exception {
        long id = confirmingStory("이야기 " + FakeAiClient.OUTPUT_REVISE_MARKER);
        confirm(id);

        pollTimes(POLLS_TO_REVIEW);

        assertThat(storyStatus(id)).isEqualTo("FAILED");
        generation(id).andExpect(jsonPath("$.error").value("OUTPUT_UNSAFE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM story_versions WHERE story_id = ?", Integer.class, id))
                .isZero();
        Map<String, Object> flag = jdbc.queryForMap("SELECT source, level, occurrence_no FROM safety_flags WHERE story_id = ?", id);
        assertThat(flag.get("source")).isEqualTo("OUTPUT");
        assertThat(flag.get("level")).isEqualTo("REVISE");

        // 다시 시도는 retry가 아니라 regenerate(첫 생성이라 기준 null, 범위 CHARACTER_STORY)를 부른다
        parent.post(storyUrl(id) + "/retry", null).andExpect(status().isAccepted());
        assertThat(ai.retryCalls(threadId(id))).isZero();
        assertThat(ai.regenerateCalls(threadId(id))).isOne();
        assertThat(ai.lastBaseCheckpointId(threadId(id))).isNull();
        assertThat(ai.lastRegenerateScope(threadId(id))).isEqualTo(RegenerateScope.CHARACTER_STORY);
        pollTimes(POLLS_TO_REVIEW);
        assertThat(storyStatus(id)).isEqualTo("REVIEWING");
        assertThat(regenerateCount(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT trigger_type FROM generation_runs WHERE story_id = ? AND run_no = 2",
                String.class, id)).isEqualTo("RETRY");

        // 부모 메모의 첫 REVISE는 차단이 아니라 다시 쓰기 안내다
        regenerate(id, """
                {"scope": "TEXT", "feedback": "[[REVISE]]"}""")
                .andExpect(jsonPath("$.safety.action").value("ASK_REVISE"))
                .andExpect(jsonPath("$.status").value("REVIEWING"));
        assertThat(jdbc.queryForList("SELECT occurrence_no FROM safety_flags WHERE story_id = ? ORDER BY id",
                Integer.class, id)).containsExactly(1, 2);
    }

    @Test
    void 다른_동화의_파일_key를_주면_저장하지_않고_실패() throws Exception {
        long id = confirmingStory(MESSAGE);
        confirm(id);
        ai.misdirectOutput();

        pollTimes(POLLS_TO_REVIEW);

        assertThat(storyStatus(id)).isEqualTo("FAILED");
        generation(id).andExpect(jsonPath("$.error").value("INVALID_OUTPUT_PATH"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM story_versions WHERE story_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void 문자_알림에_동의했으면_시작과_완료를_한_번씩_기록한다() throws Exception {
        String phone = newPhone();
        parent = signUpWithSms(phone);
        long id = confirmingStory(MESSAGE);

        confirm(id);
        assertThat(sms.lastText(phone)).contains("만들기 시작했어요");
        pollTimes(POLLS_TO_REVIEW);

        assertThat(notificationTypes(id)).containsExactlyInAnyOrder("STORY_STARTED", "STORY_COMPLETED");
        assertThat(jdbc.queryForList("SELECT status FROM notifications WHERE story_id = ?", String.class, id))
                .containsOnly("SENT");
        assertThat(jdbc.queryForList("SELECT template_code FROM notifications WHERE story_id = ?", String.class, id))
                .containsExactlyInAnyOrder("STORY_STARTED_V1", "STORY_COMPLETED_V1");
        String text = sms.lastText(phone);
        assertThat(text).contains("완성됐어요").doesNotContain(FakeAiClient.SAMPLE.title(0)).doesNotContain(MESSAGE);

        // 같은 실행의 같은 알림은 다시 보내지 않는다
        long runId = jdbc.queryForObject("SELECT id FROM generation_runs WHERE story_id = ?", Long.class, id);
        notificationService.notify(runId, NotificationType.STORY_COMPLETED);
        assertThat(notifications(id)).isEqualTo(2);

        // 다시 만들기는 시작 알림 없이 완료만
        regenerate(id, """
                {"scope": "IMAGES"}""").andExpect(status().isAccepted());
        pollTimes(POLLS_TO_REVIEW_IMAGES);
        assertThat(jdbc.queryForList("""
                SELECT n.type FROM notifications n JOIN generation_runs r ON r.id = n.generation_run_id
                WHERE r.story_id = ? AND r.run_no = 2""", String.class, id)).containsExactly("STORY_COMPLETED");
    }

    @Test
    void 문자_알림에_동의하지_않았으면_기록하지_않는다() throws Exception {
        long id = reviewingStory();

        assertThat(notifications(id)).isZero();
    }

    @Test
    void pollOnce를_동시에_불러도_버전은_한_번만_생긴다() throws Exception {
        long id = confirmingStory(MESSAGE);
        confirm(id);
        pollTimes(POLLS_TO_REVIEW - 1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> poll = () -> {
                start.await();
                return poller.pollOnce();
            };
            Future<Integer> first = executor.submit(poll);
            Future<Integer> second = executor.submit(poll);
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }
        pollTimes(2);

        assertThat(storyStatus(id)).isEqualTo("REVIEWING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM story_versions WHERE story_id = ?", Integer.class, id))
                .isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM story_pages p JOIN story_versions v ON v.id = p.version_id "
                + "WHERE v.story_id = ?", Integer.class, id)).isEqualTo(10);
    }

    @Test
    void 다른_회원의_동화는_생성_검토_API가_모두_404() throws Exception {
        long id = reviewingStory();
        Browser other = signUp(newEmail(), newPhone());

        assertError(other.post(storyUrl(id) + "/confirm", null), ErrorCode.STORY_NOT_FOUND);
        assertError(other.get(storyUrl(id) + "/generation"), ErrorCode.STORY_NOT_FOUND);
        assertError(other.get(storyUrl(id) + "/preview"), ErrorCode.STORY_NOT_FOUND);
        assertError(other.post(storyUrl(id) + "/regenerate", """
                {"scope": "TEXT"}"""), ErrorCode.STORY_NOT_FOUND);
        assertError(other.get(storyUrl(id) + "/versions"), ErrorCode.STORY_NOT_FOUND);
        assertError(other.put(storyUrl(id) + "/current-version", """
                {"versionId": %d}""".formatted(currentVersionId(id))), ErrorCode.STORY_NOT_FOUND);
        assertError(other.post(storyUrl(id) + "/approve", null), ErrorCode.STORY_NOT_FOUND);
        assertError(other.post(storyUrl(id) + "/retry", null), ErrorCode.STORY_NOT_FOUND);
        assertThat(storyStatus(id)).isEqualTo("REVIEWING");
    }

    private void expectProgress(long id, int stage, String label, String status) throws Exception {
        poller.pollOnce();
        generation(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.runNo").value(1))
                .andExpect(jsonPath("$.stage").value(stage))
                .andExpect(jsonPath("$.stageLabel").value(label))
                .andExpect(jsonPath("$.startedAt").isNotEmpty());
    }

    private void pollTimes(int times) {
        for (int i = 0; i < times; i++) {
            poller.pollOnce();
        }
    }

    private long reviewingStory() throws Exception {
        long id = confirmingStory(MESSAGE);
        confirm(id).andExpect(status().isAccepted());
        pollTimes(POLLS_TO_REVIEW);
        assertThat(storyStatus(id)).isEqualTo("REVIEWING");
        return id;
    }

    private long confirmingStory(String message) throws Exception {
        return confirmingStory("친구가 밀쳤어요", message);
    }

    /**
     * 입력과 인터뷰를 마친 CONFIRMING 동화.
     */
    private long confirmingStory(String description, String message) throws Exception {
        String body = parent.post("/api/stories", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.storyId")).longValue();
        parent.put(storyUrl(id) + "/situation", """
                {"timing": "PAST", "category": "FRIEND_CONFLICT", "description": "%s",
                 "reactions": ["CRIED"], "emotions": ["SAD"]}""".formatted(description))
                .andExpect(jsonPath("$.saved").value(true));
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

    private Browser signUpWithSms() throws Exception {
        return signUpWithSms(newPhone());
    }

    private Browser signUpWithSms(String phone) throws Exception {
        Browser browser = browser();
        verifyPhone(browser, phone, "SIGNUP");
        browser.post("/api/auth/signup", signupJson(newEmail(), phone, """
                {"GUARDIAN": true, "TERMS": true, "PRIVACY": true, "SMS_NOTIFY": true}"""))
                .andExpect(status().isCreated());
        return browser;
    }

    private ResultActions confirm(long id) throws Exception {
        return parent.post(storyUrl(id) + "/confirm", null);
    }

    private ResultActions generation(long id) throws Exception {
        return parent.get(storyUrl(id) + "/generation");
    }

    private ResultActions regenerate(long id, String json) throws Exception {
        return parent.post(storyUrl(id) + "/regenerate", json);
    }

    private static String storyUrl(long id) {
        return "/api/stories/" + id;
    }

    private static String imageUrl(long id, long versionId, int pageNo) {
        return storyUrl(id) + "/versions/" + versionId + "/pages/" + pageNo + "/image";
    }

    private String storyStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM stories WHERE id = ?", String.class, id);
    }

    private String threadId(long id) {
        return jdbc.queryForObject("SELECT thread_id FROM stories WHERE id = ?", String.class, id);
    }

    private long currentVersionId(long id) {
        return jdbc.queryForObject("SELECT current_version_id FROM stories WHERE id = ?", Long.class, id);
    }

    private Long parentVersionId(long versionId) {
        return jdbc.queryForObject("SELECT parent_version_id FROM story_versions WHERE id = ?", Long.class, versionId);
    }

    private int regenerateCount(long id) {
        return jdbc.queryForObject("SELECT regenerate_count FROM stories WHERE id = ?", Integer.class, id);
    }

    private int runs(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM generation_runs WHERE story_id = ?", Integer.class, id);
    }

    private String coverKey(long versionId) {
        return jdbc.queryForObject("SELECT cover_key FROM story_versions WHERE id = ?", String.class, versionId);
    }

    private List<String> imageKeys(long versionId) {
        return jdbc.queryForList("SELECT image_key FROM story_pages WHERE version_id = ? ORDER BY page_no",
                String.class, versionId);
    }

    private int notifications(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE story_id = ?", Integer.class, id);
    }

    private List<String> notificationTypes(long id) {
        return jdbc.queryForList("SELECT type FROM notifications WHERE story_id = ?", String.class, id);
    }

    private static void assertError(ResultActions result, ErrorCode expected) throws Exception {
        result.andExpect(status().is(expected.getStatus().value()))
                .andExpect(jsonPath("$.code").value(expected.name()));
    }
}
