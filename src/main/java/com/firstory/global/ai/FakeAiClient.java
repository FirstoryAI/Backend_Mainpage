package com.firstory.global.ai;

import com.firstory.domain.generation.entity.RegenerateScope;
import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.story.entity.InterviewEndReason;
import com.firstory.domain.story.entity.MessageDirection;
import com.firstory.domain.story.entity.Tone;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.storage.FileStorage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 진짜 AI 서버 없이 동화 만들기부터 확정까지 돌리기 위한 가짜 (firstory.ai.mode=fake, local과 test).
 * 동작을 예측할 수 있게 고정한다.
 *
 * <pre>
 * checkSafety      글에 [[CRISIS]], [[REVISE]], [[SENSITIVE]]가 있으면 그 level (여럿이면 이 순서로 우선), 없으면 OK
 * startInterview   질문 1
 * answerInterview  답변 2번까지는 다음 질문, 3번째 답변이나 skip이면 CONFIRM
 *                  finalContext는 받은 context에 parent.message_direction만 채운 것
 * generate         RUNNING. status를 부를 때마다 한 단계씩: design_story → write_story → illustrate → render → REVIEWING
 * regenerate       글 다시(text)는 write_story부터, 그림만 다시(images)는 illustrate_only → render → REVIEWING
 * REVIEWING        결과를 만들고 실제 PNG를 FileStorage에 쓴다 (쪽마다 다른 색, 캐릭터 시트 1개)
 *                  그림만 다시는 고른 쪽만 새로 쓰고 나머지 쪽은 기준 버전의 key를 그대로 돌려준다
 * </pre>
 * 전하고 싶은 말(parent.message)에 [[FAIL]]이 있으면 첫 생성은 write_story에서 FAILED, 다시 시도는 성공한다.
 * [[OUTPUT_REVISE]]가 있으면 첫 생성 결과의 safety가 REVISE다 (다시 시도는 OK).
 * <p>
 * thread별 상태는 메모리에만 둔다. 서버를 다시 띄우면 진행 중이던 인터뷰와 생성은 이어갈 수 없다 (AI_STATE_MISMATCH).
 */
public class FakeAiClient implements AiClient {

    public static final String FIRST_QUESTION = "아이에게 어떤 일이 있었는지 조금 더 알려 주세요";
    public static final String SUMMARY = "아이가 겪은 일과 부모님이 전하고 싶은 마음을 정리했어요.";
    public static final MessageDirection DIRECTION = MessageDirection.COMFORT_FIRST;
    public static final String FAIL_MARKER = "[[FAIL]]";
    public static final String OUTPUT_REVISE_MARKER = "[[OUTPUT_REVISE]]";
    public static final String FAILURE_CODE = "FAKE_FAILURE";
    public static final int INPUT_TOKENS = 41230;
    public static final int OUTPUT_TOKENS = 8120;

    // 이 수만큼 답하면 CONFIRM
    static final int ANSWERS_TO_CONFIRM = 3;

    private static final List<String> QUESTIONS = List.of(
            FIRST_QUESTION,
            "그때 아이는 어떤 표정이었나요?",
            "아이에게 꼭 전하고 싶은 말이 있나요?");
    private static final List<String> FULL_NODES = List.of("design_story", "write_story", "illustrate", "render");
    private static final List<String> TEXT_NODES = List.of("write_story", "illustrate", "render");
    private static final List<String> IMAGE_NODES = List.of("illustrate_only", "render");
    private static final Map<String, Integer> STAGES = Map.of("design_story", 1, "write_story", 2,
            "illustrate", 3, "illustrate_only", 3, "render", 4);
    // [[FAIL]]이면 이 노드에서 실패한다
    private static final String FAIL_AT = "write_story";

    private final FileStorage storage;
    private final int pageCount;
    private final Map<String, ThreadState> threads = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> answerCalls = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> statusCalls = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> regenerateCalls = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retryCalls = new ConcurrentHashMap<>();
    private volatile BusinessException failure;
    private volatile boolean misdirectOutput;

    public FakeAiClient(FileStorage storage, int pageCount) {
        this.storage = storage;
        this.pageCount = pageCount;
    }

    @Override
    public SafetyResult checkSafety(SafetySource source, List<String> texts, String questionContext) {
        TransactionGuard.requireNoTransaction();
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
        TransactionGuard.requireNoTransaction();
        throwIfFailing();
        threads.put(threadId, new ThreadState(context));
        return InterviewReply.question(1, FIRST_QUESTION, null);
    }

    @Override
    public InterviewReply answerInterview(String threadId, String answer) {
        TransactionGuard.requireNoTransaction();
        answerCalls.computeIfAbsent(threadId, id -> new AtomicInteger()).incrementAndGet();
        throwIfFailing();
        ThreadState state = threads.get(threadId);
        if (state == null || state.interviewDone) {
            throw new BusinessException(ErrorCode.AI_STATE_MISMATCH);
        }
        if (answer == null) {
            return finish(state, InterviewEndReason.USER_SKIP);
        }
        int answered = state.answered.incrementAndGet();
        if (answered >= ANSWERS_TO_CONFIRM) {
            return finish(state, InterviewEndReason.SUFFICIENT);
        }
        return InterviewReply.question(answered + 1, QUESTIONS.get(answered), Map.of("answered_turns", answered));
    }

    @Override
    public void generate(String threadId, UUID runId, int runNo, String outputPrefix, Tone tone) {
        TransactionGuard.requireNoTransaction();
        throwIfFailing();
        // 서버를 다시 띄워 인터뷰 기록이 없어도 local에서 생성은 해 볼 수 있게 빈 context로 시작한다
        ThreadState state = threads.computeIfAbsent(threadId, id -> new ThreadState(Map.of()));
        synchronized (state) {
            String message = state.parentMessage();
            state.start(runNo, outputPrefix, FULL_NODES, RegenerateScope.ALL, List.of(), null,
                    message.contains(FAIL_MARKER), message.contains(OUTPUT_REVISE_MARKER));
        }
    }

    @Override
    public AiGenerationStatus status(String threadId) {
        TransactionGuard.requireNoTransaction();
        statusCalls.computeIfAbsent(threadId, id -> new AtomicInteger()).incrementAndGet();
        throwIfFailing();
        ThreadState state = existing(threadId);
        synchronized (state) {
            if (state.runState == AiRunState.RUNNING) {
                if (state.step < state.nodes.size()) {
                    String node = state.nodes.get(state.step++);
                    state.node = node;
                    if (state.failThisRun && node.equals(FAIL_AT)) {
                        state.runState = AiRunState.FAILED;
                        state.error = FAILURE_CODE;
                    }
                } else {
                    state.result = buildResult(state);
                    state.byCheckpoint.put(state.result.checkpointId(), state.result);
                    state.runState = AiRunState.REVIEWING;
                }
            }
            return new AiGenerationStatus(state.runState, state.runNo, state.node,
                    state.node == null ? null : STAGES.get(state.node), state.error);
        }
    }

    @Override
    public AiGenerationResult result(String threadId) {
        TransactionGuard.requireNoTransaction();
        throwIfFailing();
        ThreadState state = existing(threadId);
        synchronized (state) {
            if (state.runState != AiRunState.REVIEWING) {
                throw new BusinessException(ErrorCode.AI_STATE_MISMATCH);
            }
            return state.result;
        }
    }

    @Override
    public void approve(String threadId) {
        TransactionGuard.requireNoTransaction();
        throwIfFailing();
        ThreadState state = existing(threadId);
        synchronized (state) {
            if (state.runState != AiRunState.REVIEWING) {
                throw new BusinessException(ErrorCode.AI_STATE_MISMATCH);
            }
            state.runState = AiRunState.COMPLETED;
        }
    }

    @Override
    public void regenerate(String threadId, RegenerateScope scope, List<Integer> pages, String feedback,
                           String baseCheckpointId, UUID runId, int runNo, String outputPrefix) {
        TransactionGuard.requireNoTransaction();
        throwIfFailing();
        regenerateCalls.computeIfAbsent(threadId, id -> new AtomicInteger()).incrementAndGet();
        ThreadState state = existing(threadId);
        synchronized (state) {
            // base_checkpoint_id가 null이면 thread의 현재 상태(마지막 결과)에서 다시 만든다
            AiGenerationResult base = baseCheckpointId == null ? state.result : state.byCheckpoint.get(baseCheckpointId);
            if (state.runState != AiRunState.REVIEWING || base == null) {
                throw new BusinessException(ErrorCode.AI_STATE_MISMATCH);
            }
            List<String> nodes = switch (scope) {
                case IMAGES -> IMAGE_NODES;
                case TEXT -> TEXT_NODES;
                case CHARACTER_STORY, ALL -> FULL_NODES;
            };
            state.lastBaseCheckpointId = baseCheckpointId;
            state.lastRegenerateScope = scope;
            state.start(runNo, outputPrefix, nodes, scope, pages, base, false, false);
        }
    }

    @Override
    public void retry(String threadId, UUID runId, int runNo, String outputPrefix) {
        TransactionGuard.requireNoTransaction();
        throwIfFailing();
        retryCalls.computeIfAbsent(threadId, id -> new AtomicInteger()).incrementAndGet();
        ThreadState state = existing(threadId);
        synchronized (state) {
            // 진짜 AI처럼 FAILED에서만 받는다. 결과 위기 신호로 실패한 경우(AI는 REVIEWING) Spring은 regenerate를 부른다
            if (state.runState != AiRunState.FAILED) {
                throw new BusinessException(ErrorCode.AI_STATE_MISMATCH);
            }
            state.start(runNo, outputPrefix, state.nodes, state.scope, state.pages, state.base, false, false);
        }
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

    /**
     * 테스트용. 실패 설정과 다른 동화 경로 설정(misdirectOutput)을 모두 되돌린다.
     */
    public void clearFailure() {
        this.failure = null;
        this.misdirectOutput = false;
    }

    /**
     * 테스트용. 다음에 만드는 결과의 파일 key를 다른 동화(stories/0/) 아래로 준다.
     */
    public void misdirectOutput() {
        this.misdirectOutput = true;
    }

    /**
     * 테스트용. answerInterview가 불린 횟수 (실패한 호출 포함).
     */
    public int answerCalls(String threadId) {
        return count(answerCalls, threadId);
    }

    /**
     * 테스트용. status가 불린 횟수 (실패한 호출 포함).
     */
    public int statusCalls(String threadId) {
        return count(statusCalls, threadId);
    }

    public int regenerateCalls(String threadId) {
        return count(regenerateCalls, threadId);
    }

    public int retryCalls(String threadId) {
        return count(retryCalls, threadId);
    }

    /**
     * 테스트용. 마지막 regenerate에 받은 범위.
     */
    public RegenerateScope lastRegenerateScope(String threadId) {
        ThreadState state = threads.get(threadId);
        return state == null ? null : state.lastRegenerateScope;
    }

    /**
     * 테스트용. 마지막 regenerate에 받은 기준 checkpointId. null이면 현재 상태에서 다시 만든 것.
     */
    public String lastBaseCheckpointId(String threadId) {
        ThreadState state = threads.get(threadId);
        return state == null ? null : state.lastBaseCheckpointId;
    }

    public static String checkpointId(int runNo) {
        return "fake-cp-" + runNo;
    }

    private static int count(Map<String, AtomicInteger> calls, String threadId) {
        AtomicInteger count = calls.get(threadId);
        return count == null ? 0 : count.get();
    }

    private ThreadState existing(String threadId) {
        ThreadState state = threads.get(threadId);
        if (state == null) {
            throw new BusinessException(ErrorCode.AI_STATE_MISMATCH);
        }
        return state;
    }

    private void throwIfFailing() {
        BusinessException current = failure;
        if (current != null) {
            throw new BusinessException(current.getErrorCode());
        }
    }

    private InterviewReply finish(ThreadState state, InterviewEndReason reason) {
        state.interviewDone = true;
        return InterviewReply.confirm(SUMMARY, reason, finalContext(state.context));
    }

    private AiGenerationResult buildResult(ThreadState state) {
        int runNo = state.runNo;
        String prefix = misdirectOutput ? "stories/0/runs/" + runNo + "/" : state.outputPrefix;
        AiGenerationResult base = state.scope == RegenerateScope.IMAGES ? state.base : null;
        List<Integer> targets = state.pages.isEmpty() ? null : state.pages;

        List<AiGenerationResult.Page> pages = new ArrayList<>();
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            boolean redraw = base == null || targets == null || targets.contains(pageNo);
            AiGenerationResult.Page previous = base == null ? null : base.pages().get(pageNo - 1);
            String text = previous != null ? previous.text() : pageNo + "쪽 이야기 (실행 " + runNo + ")";
            String key;
            if (redraw) {
                key = prefix + "images/page-%02d.png".formatted(pageNo);
                storage.write(key, png(color(pageNo, runNo)), "image/png");
            } else {
                key = previous.imagePath();
            }
            pages.add(new AiGenerationResult.Page(pageNo, text, "page " + pageNo, key, "done"));
        }

        List<AiGenerationResult.CharacterSheet> sheets;
        if (base != null) {
            sheets = base.characterSheets();
        } else {
            String sheetKey = prefix + "images/sheet-main-1.png";
            storage.write(sheetKey, png(Color.GRAY), "image/png");
            sheets = List.of(new AiGenerationResult.CharacterSheet("main", "아로", sheetKey));
        }

        AiGenerationResult.Guide guide = new AiGenerationResult.Guide(
                "아이가 이야기 속 마음을 알아차리도록 도와요.", List.of("천천히 읽어 주세요.", "그림을 함께 봐요."),
                "너라면 어떻게 했을까?", "정하지 못해도 괜찮아요.",
                List.of(new AiGenerationResult.Prompt(3, "아로는 지금 어떤 마음일까?", "emotion", "표정을 함께 봐요."),
                        new AiGenerationResult.Prompt(6, "너라면 친구에게 뭐라고 말할까?", "action", "답을 기다려 주세요.")));
        SafetyResult safety = state.unsafeOutput
                ? new SafetyResult(SafetyLevel.REVISE, "harmful_discipline", "완성된 이야기에 아이에게 맞지 않는 표현이 있어요.")
                : SafetyResult.ok();
        return new AiGenerationResult(runNo, checkpointId(runNo), "가짜 동화 " + runNo,
                Map.of("hero", "아로", "run_no", runNo), sheets, pages, guide,
                new AiGenerationResult.Cost(INPUT_TOKENS, OUTPUT_TOKENS, pageCount + 1), safety);
    }

    private Color color(int pageNo, int runNo) {
        return Color.getHSBColor((pageNo - 1) / (float) pageCount, 0.45f + 0.1f * (runNo % 5), 0.95f);
    }

    private static byte[] png(Color color) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 16, 16);
        graphics.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
        private final Map<String, AiGenerationResult> byCheckpoint = new ConcurrentHashMap<>();
        private boolean interviewDone;

        private AiRunState runState;
        private int runNo;
        private String outputPrefix;
        private List<String> nodes = List.of();
        private int step;
        private String node;
        private String error;
        private RegenerateScope scope;
        private List<Integer> pages = List.of();
        private AiGenerationResult base;
        private AiGenerationResult result;
        private boolean failThisRun;
        private boolean unsafeOutput;
        private String lastBaseCheckpointId;
        private RegenerateScope lastRegenerateScope;

        private ThreadState(Map<String, Object> context) {
            this.context = new LinkedHashMap<>(context);
        }

        private void start(int runNo, String outputPrefix, List<String> nodes, RegenerateScope scope,
                           List<Integer> pages, AiGenerationResult base, boolean failThisRun, boolean unsafeOutput) {
            this.runState = AiRunState.RUNNING;
            this.runNo = runNo;
            this.outputPrefix = outputPrefix;
            this.nodes = nodes;
            this.step = 0;
            this.node = null;
            this.error = null;
            this.scope = scope;
            this.pages = pages == null ? List.of() : List.copyOf(pages);
            this.base = base;
            this.result = null;
            this.failThisRun = failThisRun;
            this.unsafeOutput = unsafeOutput;
        }

        private String parentMessage() {
            if (context.get("parent") instanceof Map<?, ?> parent && parent.get("message") instanceof String message) {
                return message;
            }
            return "";
        }
    }
}
