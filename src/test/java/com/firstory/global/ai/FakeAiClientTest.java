package com.firstory.global.ai;

import com.firstory.domain.generation.entity.RegenerateScope;
import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.story.entity.InterviewEndReason;
import com.firstory.global.config.FirstoryProperties.Ai.Fake;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.storage.LocalFileStorage;
import com.firstory.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeAiClientTest {

    static final String THREAD_ID = "thread-1";
    static final int PAGE_COUNT = 10;
    static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47};

    @TempDir
    Path storageRoot;

    LocalFileStorage storage;
    FakeAiClient client;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(storageRoot);
        client = new FakeAiClient(storage, PAGE_COUNT);
    }

    @ParameterizedTest
    @EnumSource(value = SafetyLevel.class, names = {"SENSITIVE", "REVISE", "CRISIS"})
    void 마커가_있으면_그_level(SafetyLevel level) {
        SafetyResult result = client.checkSafety(SafetySource.FORM,
                List.of("평범한 글", "글 " + FakeAiClient.marker(level) + " 끝"), null);

        assertThat(result.level()).isEqualTo(level);
        assertThat(result.category()).isNotBlank();
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void 마커가_없으면_OK() {
        assertThat(client.checkSafety(SafetySource.INTERVIEW, List.of("할머니가 돌아가셨어요"), "질문"))
                .isEqualTo(SafetyResult.ok());
    }

    @Test
    void 마커가_여럿이면_CRISIS_REVISE_SENSITIVE_순서() {
        assertThat(client.checkSafety(SafetySource.FORM, List.of("[[SENSITIVE]] [[CRISIS]] [[REVISE]]"), null)
                .level()).isEqualTo(SafetyLevel.CRISIS);
        assertThat(client.checkSafety(SafetySource.FORM, List.of("[[SENSITIVE]] [[REVISE]]"), null)
                .level()).isEqualTo(SafetyLevel.REVISE);
    }

    @Test
    void 답변_2번까지는_다음_질문이고_3번째_답변이면_CONFIRM() {
        InterviewReply first = client.startInterview(THREAD_ID, UUID.randomUUID(), context(), null);
        assertThat(first).isEqualTo(InterviewReply.question(1, FakeAiClient.FIRST_QUESTION, null));

        InterviewReply second = client.answerInterview(THREAD_ID, "답변 1");
        InterviewReply third = client.answerInterview(THREAD_ID, "답변 2");
        assertThat(second.type()).isEqualTo(InterviewReply.Type.QUESTION);
        assertThat(second.turn()).isEqualTo(2);
        assertThat(third.turn()).isEqualTo(3);

        InterviewReply confirm = client.answerInterview(THREAD_ID, "답변 3");
        assertThat(confirm.isConfirm()).isTrue();
        assertThat(confirm.summary()).isEqualTo(FakeAiClient.SUMMARY);
        assertThat(confirm.endReason()).isEqualTo(InterviewEndReason.SUFFICIENT);
        assertThat(client.answerCalls(THREAD_ID)).isEqualTo(3);
    }

    @Test
    void skip이면_USER_SKIP() {
        client.startInterview(THREAD_ID, UUID.randomUUID(), context(), null);
        client.answerInterview(THREAD_ID, "답변 1");

        InterviewReply reply = client.answerInterview(THREAD_ID, null);

        assertThat(reply.isConfirm()).isTrue();
        assertThat(reply.endReason()).isEqualTo(InterviewEndReason.USER_SKIP);
    }

    @Test
    @SuppressWarnings("unchecked")
    void finalContext는_받은_context에_message_direction만_채운다() {
        Map<String, Object> context = context();
        client.startInterview(THREAD_ID, UUID.randomUUID(), context, null);

        Map<String, Object> finalContext = client.answerInterview(THREAD_ID, null).finalContext();

        assertThat(finalContext.get("situation")).isEqualTo(context.get("situation"));
        Map<String, Object> parent = (Map<String, Object>) finalContext.get("parent");
        assertThat(parent).containsEntry("message", "용기를 주고 싶어요")
                .containsEntry("message_direction", "comfort_first")
                .containsEntry("response", null);
    }

    @Test
    void 시작하지_않은_thread에_답하면_AI_STATE_MISMATCH() {
        assertThatThrownBy(() -> client.answerInterview("unknown", "답변"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_STATE_MISMATCH);
    }

    @Test
    void 실패하게_설정하면_모든_호출이_그_예외를_던진다() {
        client.failWith(ErrorCode.AI_UNAVAILABLE);

        assertThatThrownBy(() -> client.checkSafety(SafetySource.FORM, List.of("글"), null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.startInterview(THREAD_ID, UUID.randomUUID(), context(), null))
                .isInstanceOf(BusinessException.class);

        client.clearFailure();
        assertThat(client.checkSafety(SafetySource.FORM, List.of("글"), null)).isEqualTo(SafetyResult.ok());
    }

    @Test
    void 생성은_status를_부를_때마다_한_단계씩_진행하고_5번째에_REVIEWING() {
        startGeneration(context());

        assertThat(client.status(THREAD_ID)).isEqualTo(
                new AiGenerationStatus(AiRunState.RUNNING, 1, "design_story", 1, null));
        assertThat(client.status(THREAD_ID).currentNode()).isEqualTo("write_story");
        assertThat(client.status(THREAD_ID).currentNode()).isEqualTo("illustrate");
        assertThat(client.status(THREAD_ID).currentNode()).isEqualTo("render");
        assertThat(client.status(THREAD_ID).state()).isEqualTo(AiRunState.REVIEWING);
        assertThat(client.status(THREAD_ID).state()).as("REVIEWING에서는 그대로").isEqualTo(AiRunState.REVIEWING);
        assertThat(client.statusCalls(THREAD_ID)).isEqualTo(6);

        AiGenerationResult result = client.result(THREAD_ID);
        assertThat(result.title()).isEqualTo("공룡 아로의 첫 인사");
        assertThat(result.checkpointId()).isEqualTo("fake-cp-1");
        assertThat(result.pages()).hasSize(PAGE_COUNT);
        assertThat(result.pages().getFirst().text()).isEqualTo("아로는 작은 초록 공룡이에요. 오늘은 새 어린이집에 가는 날이에요.");
        assertThat(result.pages().get(1).text()).contains("**콩닥콩닥**");
        assertThat(result.guide().prompts()).extracting(AiGenerationResult.Prompt::afterPage).containsExactly(4, 8);
        assertThat(result.guide().prompts().get(1).parentHint()).isNull();
        assertThat(result.guide().ifUndecided()).isNotBlank();
        assertThat(result.safety()).isEqualTo(SafetyResult.ok());
        // 실제 PNG 파일이 저장소에 있다
        for (AiGenerationResult.Page page : result.pages()) {
            assertThat(page.imagePath()).startsWith("stories/12/runs/1/images/page-");
            assertThat(storage.read(page.imagePath()).orElseThrow().bytes()).startsWith(PNG_SIGNATURE);
        }
        assertThat(result.characterSheets()).singleElement()
                .satisfies(sheet -> assertThat(storage.exists(sheet.path())).isTrue());
    }

    @Test
    void 그림만_다시는_고른_쪽만_새_key이고_나머지는_기준_버전의_key() {
        AiGenerationResult first = generateToReview();

        client.regenerate(THREAD_ID, RegenerateScope.IMAGES, List.of(3, 5), null, first.checkpointId(),
                UUID.randomUUID(), 2, "stories/12/runs/2/");
        assertThat(client.status(THREAD_ID).currentNode()).isEqualTo("illustrate_only");
        assertThat(client.status(THREAD_ID).currentNode()).isEqualTo("render");
        assertThat(client.status(THREAD_ID).state()).isEqualTo(AiRunState.REVIEWING);

        AiGenerationResult second = client.result(THREAD_ID);
        for (int pageNo = 1; pageNo <= PAGE_COUNT; pageNo++) {
            String key = second.pages().get(pageNo - 1).imagePath();
            if (pageNo == 3 || pageNo == 5) {
                assertThat(key).isEqualTo("stories/12/runs/2/images/page-%02d.png".formatted(pageNo));
            } else {
                assertThat(key).isEqualTo(first.pages().get(pageNo - 1).imagePath());
            }
        }
        assertThat(second.characterSheets()).isEqualTo(first.characterSheets());
        assertThat(second.checkpointId()).isEqualTo("fake-cp-2");
        assertThat(client.lastBaseCheckpointId(THREAD_ID)).isEqualTo("fake-cp-1");
    }

    @Test
    void 글_다시는_write_story부터() {
        AiGenerationResult first = generateToReview();

        client.regenerate(THREAD_ID, RegenerateScope.TEXT, List.of(), "더 밝게", first.checkpointId(),
                UUID.randomUUID(), 2, "stories/12/runs/2/");

        assertThat(client.status(THREAD_ID).currentNode()).isEqualTo("write_story");
    }

    @Test
    void 없는_checkpoint에서_다시_만들면_AI_STATE_MISMATCH() {
        generateToReview();

        assertThatThrownBy(() -> client.regenerate(THREAD_ID, RegenerateScope.TEXT, List.of(), null, "unknown",
                UUID.randomUUID(), 2, "stories/12/runs/2/"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_STATE_MISMATCH);
    }

    @Test
    void FAIL_마커가_있으면_첫_생성은_실패하고_다시_시도는_성공() {
        startGeneration(contextWithMessage("용기 " + FakeAiClient.FAIL_MARKER));

        assertThat(client.status(THREAD_ID).state()).isEqualTo(AiRunState.RUNNING);
        AiGenerationStatus failed = client.status(THREAD_ID);
        assertThat(failed.state()).isEqualTo(AiRunState.FAILED);
        assertThat(failed.error()).isEqualTo(FakeAiClient.FAILURE_CODE);

        client.retry(THREAD_ID, UUID.randomUUID(), 2, "stories/12/runs/2/");
        AiGenerationStatus last = null;
        for (int i = 0; i < 5; i++) {
            last = client.status(THREAD_ID);
        }
        assertThat(last.state()).isEqualTo(AiRunState.REVIEWING);
        assertThat(client.result(THREAD_ID).runNo()).isEqualTo(2);
    }

    @Test
    void OUTPUT_REVISE_마커가_있으면_결과_safety가_REVISE() {
        startGeneration(contextWithMessage(FakeAiClient.OUTPUT_REVISE_MARKER));
        for (int i = 0; i < 5; i++) {
            client.status(THREAD_ID);
        }

        assertThat(client.result(THREAD_ID).safety().level()).isEqualTo(SafetyLevel.REVISE);
    }

    @Test
    void 다른_동화_경로로_주게_설정할_수_있다() {
        client.misdirectOutput();

        AiGenerationResult result = generateToReview();

        assertThat(result.fileKeys()).allMatch(key -> key.startsWith("stories/0/"));
    }

    @Test
    void 기준_checkpoint가_null이면_현재_결과에서_다시_만든다() {
        generateToReview();

        client.regenerate(THREAD_ID, RegenerateScope.IMAGES, List.of(2), null, null, UUID.randomUUID(), 2,
                "stories/12/runs/2/");
        for (int i = 0; i < 3; i++) {
            client.status(THREAD_ID);
        }

        AiGenerationResult result = client.result(THREAD_ID);
        assertThat(result.pages().get(0).imagePath()).isEqualTo("stories/12/runs/1/images/page-01.png");
        assertThat(result.pages().get(1).imagePath()).isEqualTo("stories/12/runs/2/images/page-02.png");
        assertThat(client.lastBaseCheckpointId(THREAD_ID)).isNull();
        assertThat(client.lastRegenerateScope(THREAD_ID)).isEqualTo(RegenerateScope.IMAGES);
        assertThat(client.regenerateCalls(THREAD_ID)).isOne();
    }

    @Test
    void 부모_검토에서_멈춘_상태에서는_retry를_받지_않는다() {
        generateToReview();

        assertThatThrownBy(() -> client.retry(THREAD_ID, UUID.randomUUID(), 2, "stories/12/runs/2/"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_STATE_MISMATCH);
    }

    @Test
    void 확정하면_COMPLETED() {
        generateToReview();

        client.approve(THREAD_ID);

        assertThat(client.status(THREAD_ID).state()).isEqualTo(AiRunState.COMPLETED);
    }

    @Test
    void 다시_만들기면_제목_뒤에_다시_n() {
        AiGenerationResult first = generateToReview();

        client.regenerate(THREAD_ID, RegenerateScope.TEXT, List.of(), null, first.checkpointId(),
                UUID.randomUUID(), 2, "stories/12/runs/2/");
        for (int i = 0; i < 4; i++) {
            client.status(THREAD_ID);
        }

        assertThat(client.result(THREAD_ID).title()).isEqualTo("공룡 아로의 첫 인사 (다시 1)");
    }

    @Test
    void stage_duration이_있으면_시작_후_지난_시간으로_단계를_정한다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
        client = new FakeAiClient(storage, PAGE_COUNT, new Fake(null, null, Duration.ofSeconds(40), 2), clock);
        startGeneration(context());

        assertThat(client.status(THREAD_ID).currentNode()).as("0초").isEqualTo("design_story");
        assertThat(client.status(THREAD_ID).currentNode()).as("다시 불러도 그대로").isEqualTo("design_story");
        clock.advance(Duration.ofSeconds(39));
        assertThat(client.status(THREAD_ID).currentNode()).isEqualTo("design_story");
        clock.advance(Duration.ofSeconds(1));
        assertThat(client.status(THREAD_ID).currentNode()).as("40초").isEqualTo("write_story");
        clock.advance(Duration.ofSeconds(80));
        assertThat(client.status(THREAD_ID)).as("120초").isEqualTo(
                new AiGenerationStatus(AiRunState.RUNNING, 1, "render", 4, null));
        clock.advance(Duration.ofSeconds(40));
        assertThat(client.status(THREAD_ID).state()).as("160초").isEqualTo(AiRunState.REVIEWING);
    }

    @Test
    void stage_duration이_있어도_FAIL이면_write_story에서_실패() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
        client = new FakeAiClient(storage, PAGE_COUNT, new Fake(null, null, Duration.ofSeconds(40), 2), clock);
        startGeneration(contextWithMessage(FakeAiClient.FAIL_MARKER));

        clock.advance(Duration.ofMinutes(10));

        assertThat(client.status(THREAD_ID)).isEqualTo(
                new AiGenerationStatus(AiRunState.FAILED, 1, "write_story", 2, FakeAiClient.FAILURE_CODE));
    }

    @Test
    void turns가_0이면_thread_id로_질문_수_1에서_5개를_정하고_같은_thread는_항상_같다() {
        client = new FakeAiClient(storage, PAGE_COUNT, new Fake(null, null, null, 0), Clock.systemUTC());

        Set<Integer> counts = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String threadId = "story-" + i;
            int questions = questionCount(threadId);
            assertThat(questions).isBetween(1, Fake.MAX_QUESTIONS);
            assertThat(questionCount(threadId)).isEqualTo(questions);
            counts.add(questions);
        }
        assertThat(counts).hasSizeGreaterThan(1);
    }

    @Test
    void 질문은_샘플_순서대로() {
        client = new FakeAiClient(storage, PAGE_COUNT, new Fake(null, null, null, 4), Clock.systemUTC());
        client.startInterview(THREAD_ID, UUID.randomUUID(), context(), null);

        List<String> questions = new ArrayList<>(List.of(FakeAiClient.FIRST_QUESTION));
        InterviewReply reply;
        while (!(reply = client.answerInterview(THREAD_ID, "답변")).isConfirm()) {
            questions.add(reply.question());
        }

        assertThat(questions).isEqualTo(FakeAiClient.SAMPLE.questions());
    }

    @Test
    void PAGE_FAIL이_답변에_있으면_5쪽_그림만_failed() {
        client.startInterview(THREAD_ID, UUID.randomUUID(), context(), null);
        client.answerInterview(THREAD_ID, "답변 " + FakeAiClient.PAGE_FAIL_MARKER);
        client.answerInterview(THREAD_ID, null);
        client.generate(THREAD_ID, UUID.randomUUID(), 1, "stories/12/runs/1/", null);
        for (int i = 0; i < 5; i++) {
            client.status(THREAD_ID);
        }

        AiGenerationResult result = client.result(THREAD_ID);
        assertThat(result.pages()).filteredOn(page -> !page.imageDone())
                .extracting(AiGenerationResult.Page::pageNo).containsExactly(FakeAiClient.FAILED_PAGE);
        assertThat(result.pages().get(FakeAiClient.FAILED_PAGE - 1).imagePath()).isNull();

        // 그 쪽만 다시 그리면 성공한다
        client.regenerate(THREAD_ID, RegenerateScope.IMAGES, List.of(FakeAiClient.FAILED_PAGE), null,
                result.checkpointId(), UUID.randomUUID(), 2, "stories/12/runs/2/");
        for (int i = 0; i < 3; i++) {
            client.status(THREAD_ID);
        }
        assertThat(client.result(THREAD_ID).pages()).allMatch(AiGenerationResult.Page::imageDone);
    }

    @Test
    void AI_DOWN을_처음_보면_그다음_호출_한_번만_AI_UNAVAILABLE() {
        String message = "용기 " + FakeAiClient.AI_DOWN_MARKER;
        assertThat(client.checkSafety(SafetySource.FORM, List.of(message), null)).isEqualTo(SafetyResult.ok());

        assertThatThrownBy(() -> client.startInterview(THREAD_ID, UUID.randomUUID(), context(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_UNAVAILABLE);
        assertThat(client.startInterview(THREAD_ID, UUID.randomUUID(), context(), null).turn()).isEqualTo(1);

        // 같은 글로 다시 보내면 성공한다
        client.checkSafety(SafetySource.FORM, List.of(message), null);
        assertThat(client.answerInterview(THREAD_ID, "답변").turn()).isEqualTo(2);
    }

    @Test
    void AI_DOWN은_생성_진행_확인에는_쓰이지_않는다() {
        startGeneration(context());
        client.checkSafety(SafetySource.INTERVIEW, List.of(FakeAiClient.AI_DOWN_MARKER), "질문");

        assertThat(client.status(THREAD_ID).state()).isEqualTo(AiRunState.RUNNING);
        assertThatThrownBy(() -> client.approve(THREAD_ID))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_UNAVAILABLE);
    }

    private int questionCount(String threadId) {
        client.startInterview(threadId, UUID.randomUUID(), context(), null);
        int questions = 1;
        while (!client.answerInterview(threadId, "답변").isConfirm()) {
            questions++;
        }
        return questions;
    }

    private void startGeneration(Map<String, Object> context) {
        client.startInterview(THREAD_ID, UUID.randomUUID(), context, null);
        client.answerInterview(THREAD_ID, null);
        client.generate(THREAD_ID, UUID.randomUUID(), 1, "stories/12/runs/1/", null);
    }

    private AiGenerationResult generateToReview() {
        startGeneration(context());
        for (int i = 0; i < 5; i++) {
            client.status(THREAD_ID);
        }
        return client.result(THREAD_ID);
    }

    private static Map<String, Object> contextWithMessage(String message) {
        Map<String, Object> context = context();
        @SuppressWarnings("unchecked")
        Map<String, Object> parent = (Map<String, Object>) context.get("parent");
        parent.put("message", message);
        return context;
    }

    private static Map<String, Object> context() {
        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("response", null);
        parent.put("concern", null);
        parent.put("message", "용기를 주고 싶어요");
        parent.put("message_direction", null);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("situation", Map.of("category", "friend_conflict"));
        context.put("parent", parent);
        return context;
    }
}
