package com.firstory.global.ai;

import com.firstory.domain.generation.entity.RegenerateScope;
import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.story.entity.InterviewEndReason;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.storage.LocalFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeAiClientTest {

    static final String THREAD_ID = "thread-1";
    static final int PAGE_COUNT = 8;
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
        assertThat(result.title()).isEqualTo("가짜 동화 1");
        assertThat(result.checkpointId()).isEqualTo("fake-cp-1");
        assertThat(result.pages()).hasSize(PAGE_COUNT);
        assertThat(result.pages().getFirst().text()).isEqualTo("1쪽 이야기 (실행 1)");
        assertThat(result.guide().prompts()).extracting(AiGenerationResult.Prompt::afterPage).containsExactly(3, 6);
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
    void 확정하면_COMPLETED() {
        generateToReview();

        client.approve(THREAD_ID);

        assertThat(client.status(THREAD_ID).state()).isEqualTo(AiRunState.COMPLETED);
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
