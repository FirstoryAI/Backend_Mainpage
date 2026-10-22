package com.firstory.global.ai;

import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.story.entity.InterviewEndReason;
import com.firstory.domain.story.entity.MessageDirection;
import com.firstory.domain.story.entity.Tone;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 진짜 AI 서버 없이 동화 만들기 흐름을 돌리기 위한 가짜 (firstory.ai.mode=fake, local과 test).
 * 동작을 예측할 수 있게 고정한다.
 *
 * <pre>
 * checkSafety      글에 [[CRISIS]], [[REVISE]], [[SENSITIVE]]가 있으면 그 level (여럿이면 이 순서로 우선), 없으면 OK
 * startInterview   질문 1
 * answerInterview  답변 2번까지는 다음 질문, 3번째 답변이나 skip이면 CONFIRM
 *                  finalContext는 받은 context에 parent.message_direction만 채운 것
 * </pre>
 * thread별 상태는 메모리에만 둔다. 서버를 다시 띄우면 진행 중이던 인터뷰는 이어갈 수 없다 (AI_STATE_MISMATCH).
 */
public class FakeAiClient implements AiClient {

    public static final String FIRST_QUESTION = "아이에게 어떤 일이 있었는지 조금 더 알려 주세요";
    public static final String SUMMARY = "아이가 겪은 일과 부모님이 전하고 싶은 마음을 정리했어요.";
    public static final MessageDirection DIRECTION = MessageDirection.COMFORT_FIRST;

    // 이 수만큼 답하면 CONFIRM
    static final int ANSWERS_TO_CONFIRM = 3;

    private static final List<String> QUESTIONS = List.of(
            FIRST_QUESTION,
            "그때 아이는 어떤 표정이었나요?",
            "아이에게 꼭 전하고 싶은 말이 있나요?");

    private final Map<String, ThreadState> threads = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> answerCalls = new ConcurrentHashMap<>();
    private volatile BusinessException failure;

    @Override
    public SafetyResult checkSafety(SafetySource source, List<String> texts, String questionContext) {
        throwIfFailing();
        String joined = String.join("\n", texts);
        if (joined.contains(marker(SafetyLevel.CRISIS))) {
            return new SafetyResult(SafetyLevel.CRISIS, "abuse_neglect", "아이의 안전이 걱정되는 내용이 있어요.");
        }
        if (joined.contains(marker(SafetyLevel.REVISE))) {
            return new SafetyResult(SafetyLevel.REVISE, "harmful_discipline",
                    "아이에게 겁을 주는 방향은 동화로 만들기 어려워요. 다른 표현으로 써 주세요.");
        }
        if (joined.contains(marker(SafetyLevel.SENSITIVE))) {
            return new SafetyResult(SafetyLevel.SENSITIVE, "loss", "무거운 주제가 담겨 있어요. 이야기 분위기를 골라 주세요.");
        }
        return SafetyResult.ok();
    }

    @Override
    public InterviewReply startInterview(String threadId, UUID runId, Map<String, Object> context, Tone tone) {
        throwIfFailing();
        threads.put(threadId, new ThreadState(context));
        return InterviewReply.question(1, FIRST_QUESTION, null);
    }

    @Override
    public InterviewReply answerInterview(String threadId, String answer) {
        answerCalls.computeIfAbsent(threadId, id -> new AtomicInteger()).incrementAndGet();
        throwIfFailing();
        ThreadState state = threads.get(threadId);
        if (state == null) {
            throw new BusinessException(ErrorCode.AI_STATE_MISMATCH);
        }
        if (answer == null) {
            return finish(threadId, state, InterviewEndReason.USER_SKIP);
        }
        int answered = state.answered.incrementAndGet();
        if (answered >= ANSWERS_TO_CONFIRM) {
            return finish(threadId, state, InterviewEndReason.SUFFICIENT);
        }
        return InterviewReply.question(answered + 1, QUESTIONS.get(answered), Map.of("answered_turns", answered));
    }

    public static String marker(SafetyLevel level) {
        return "[[" + level.name() + "]]";
    }

    /**
     * 테스트용. 다음 호출부터 이 예외를 던진다. clearFailure()로 되돌린다.
     */
    public void failWith(ErrorCode errorCode) {
        this.failure = new BusinessException(errorCode);
    }

    public void clearFailure() {
        this.failure = null;
    }

    /**
     * 테스트용. answerInterview가 불린 횟수 (실패한 호출 포함).
     */
    public int answerCalls(String threadId) {
        AtomicInteger calls = answerCalls.get(threadId);
        return calls == null ? 0 : calls.get();
    }

    private void throwIfFailing() {
        BusinessException current = failure;
        if (current != null) {
            throw new BusinessException(current.getErrorCode());
        }
    }

    private InterviewReply finish(String threadId, ThreadState state, InterviewEndReason reason) {
        threads.remove(threadId);
        return InterviewReply.confirm(SUMMARY, reason, finalContext(state.context));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> finalContext(Map<String, Object> context) {
        Map<String, Object> copy = new LinkedHashMap<>(context);
        Map<String, Object> parent = new LinkedHashMap<>();
        if (copy.get("parent") instanceof Map<?, ?> original) {
            parent.putAll((Map<String, Object>) original);
        }
        parent.put("message_direction", DIRECTION.aiValue());
        copy.put("parent", parent);
        return copy;
    }

    private static final class ThreadState {

        private final Map<String, Object> context;
        private final AtomicInteger answered = new AtomicInteger();

        private ThreadState(Map<String, Object> context) {
            this.context = new LinkedHashMap<>(context);
        }
    }
}
