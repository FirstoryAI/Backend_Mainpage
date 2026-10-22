package com.firstory.global.ai;

import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.story.entity.InterviewEndReason;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeAiClientTest {

    static final String THREAD_ID = "thread-1";

    FakeAiClient client = new FakeAiClient();

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
