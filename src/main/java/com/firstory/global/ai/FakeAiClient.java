package com.firstory.global.ai;

import com.firstory.domain.generation.entity.RegenerateScope;
import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.story.entity.InterviewEndReason;
import com.firstory.domain.story.entity.MessageDirection;
import com.firstory.domain.story.entity.Tone;
import com.firstory.global.config.FirstoryProperties.Ai.Fake;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.storage.FileStorage;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 진짜 AI 서버 없이 동화 만들기부터 확정까지 돌리기 위한 가짜 (firstory.ai.mode=fake, local과 test).
 * 동작을 예측할 수 있게 고정한다. 속도와 질문 수는 firstory.ai.fake.*로 진짜처럼 바꿀 수 있다 (local).
 *
 * <pre>
 * checkSafety      글에 [[CRISIS]], [[REVISE]], [[SENSITIVE]]가 있으면 그 level (여럿이면 이 순서로 우선), 없으면 OK
 * startInterview   질문 1
 * answerInterview  답변 turns번까지는 다음 질문, 그다음 답변이나 skip이면 CONFIRM (turns 2면 3번째 답변에서)
 *                  turns가 0이면 thread_id로 질문 수 1~5개를 정한다 (같은 동화는 항상 같은 수)
 *                  finalContext는 받은 context에 parent.message_direction만 채운 것
 * generate         RUNNING. design_story → write_story → illustrate → render → REVIEWING
 *                  stage-duration이 0이면 status를 부를 때마다 한 단계씩, 아니면 시작 후 지난 시간 / stage-duration 단계
 * regenerate       글 다시(text)는 write_story부터, 그림만 다시(images)는 illustrate_only → render → REVIEWING
 * REVIEWING        fake-ai/sample-story.json의 글과 가이드로 결과를 만들고 실제 PNG를 FileStorage에 쓴다
 *                  (쪽마다 다른 색, 캐릭터 시트 1개). 다시 만들기면 제목 뒤에 (다시 n)
 *                  그림만 다시는 고른 쪽만 새로 쓰고 나머지 쪽은 기준 버전의 key를 그대로 돌려준다
 * </pre>
 * 전하고 싶은 말(parent.message)이나 인터뷰 답변에 넣는 표시:
 * <pre>
 * [[FAIL]]           첫 생성은 write_story에서 FAILED, 다시 시도는 성공한다
 * [[OUTPUT_REVISE]]  첫 생성 결과의 safety가 REVISE (다시 시도는 OK)
 * [[PAGE_FAIL]]      첫 생성(과 그 다시 시도) 결과에서 5쪽 그림만 failed
 * [[AI_DOWN]]        위기 신호 검사에서 처음 본 글이면, 그다음 AI 호출 한 번이 AI_UNAVAILABLE
 *                    (생성 진행 확인 status, result는 세지 않는다. 같은 글로 다시 보내면 성공한다)
 * </pre>
 * thread별 상태는 메모리에만 둔다. 서버를 다시 띄우면 진행 중이던 인터뷰와 생성은 이어갈 수 없다 (AI_STATE_MISMATCH).
 */
public class FakeAiClient implements AiClient {

    public static final Sample SAMPLE = Sample.load();
    public static final String FIRST_QUESTION = SAMPLE.questions().getFirst();
    public static final String SUMMARY = "아이가 겪은 일과 부모님이 전하고 싶은 마음을 정리했어요.";
    public static final MessageDirection DIRECTION = MessageDirection.COMFORT_FIRST;
    public static final String FAIL_MARKER = "[[FAIL]]";
    public static final String OUTPUT_REVISE_MARKER = "[[OUTPUT_REVISE]]";
    public static final String PAGE_FAIL_MARKER = "[[PAGE_FAIL]]";
    public static final String AI_DOWN_MARKER = "[[AI_DOWN]]";
    // [[PAGE_FAIL]]이면 이 쪽 그림만 실패한다
    public static final int FAILED_PAGE = 5;
    public static final String FAILURE_CODE = "FAKE_FAILURE";
    public static final int INPUT_TOKENS = 41230;
    public static final int OUTPUT_TOKENS = 8120;

    private static final String SAMPLE_PATH = "/fake-ai/sample-story.json";
    private static final int IMAGE_SIZE = 512;
    private static final List<String> FULL_NODES = List.of("design_story", "write_story", "illustrate", "render");
    private static final List<String> TEXT_NODES = List.of("write_story", "illustrate", "render");
    private static final List<String> IMAGE_NODES = List.of("illustrate_only", "render");
    private static final Map<String, Integer> STAGES = Map.of("design_story", 1, "write_story", 2,
            "illustrate", 3, "illustrate_only", 3, "render", 4);
    // [[FAIL]]이면 이 노드에서 실패한다
    private static final String FAIL_AT = "write_story";

    private final FileStorage storage;
    private final int pageCount;
    private final Fake settings;
    private final Clock clock;
    private final Map<String, ThreadState> threads = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> answerCalls = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> statusCalls = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> regenerateCalls = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retryCalls = new ConcurrentHashMap<>();
    // [[AI_DOWN]]을 이미 본 글 (같은 글로 다시 보내면 성공하게)
    private final Set<Integer> downSeen = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean downNext = new AtomicBoolean();
    private volatile BusinessException failure;
    private volatile boolean misdirectOutput;

    public FakeAiClient(FileStorage storage, int pageCount) {
        this(storage, pageCount, Fake.INSTANT, Clock.systemUTC());
    }

    public FakeAiClient(FileStorage storage, int pageCount, Fake settings, Clock clock) {
        this.storage = storage;
        this.pageCount = pageCount;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public SafetyResult checkSafety(SafetySource source, List<String> texts, String questionContext) {
        TransactionGuard.requireNoTransaction();
        throwIfFailing();
        pause(settings.safetyDelay());
        String joined = String.join("\n", texts);
        if (joined.contains(AI_DOWN_MARKER) && downSeen.add(joined.hashCode())) {
            downNext.set(true);
        }
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
        pause(settings.interviewDelay());
        threads.put(threadId, new ThreadState(context, followUps(threadId)));
        return InterviewReply.question(1, FIRST_QUESTION, null);
    }

    @Override
    public InterviewReply answerInterview(String threadId, String answer) {
        TransactionGuard.requireNoTransaction();
        answerCalls.computeIfAbsent(threadId, id -> new AtomicInteger()).incrementAndGet();
        throwIfFailing();
        pause(settings.interviewDelay());
        ThreadState state = threads.get(threadId);
        if (state == null || state.interviewDone) {
            throw new BusinessException(ErrorCode.AI_STATE_MISMATCH);
        }
        if (answer == null) {
            return finish(state, InterviewEndReason.USER_SKIP);
        }
        state.answers.add(answer);
        int answered = state.answered.incrementAndGet();
        if (answered > state.followUps) {
            return finish(state, InterviewEndReason.SUFFICIENT);
        }
        return InterviewReply.question(answered + 1, SAMPLE.questions().get(answered),
                Map.of("answered_turns", answered));
    }

    @Override
    public void generate(String threadId, UUID runId, int runNo, String outputPrefix, Tone tone,
                         Map<String, Object> context, String summary) {
        TransactionGuard.requireNoTransaction();
        throwIfFailing();
        // 서버를 다시 띄워 인터뷰 기록이 없어도 local에서 생성은 해 볼 수 있게 빈 context로 시작한다
        ThreadState state = threads.computeIfAbsent(threadId, id -> new ThreadState(Map.of(), followUps(id)));
        synchronized (state) {
            state.lastGenerateContext = context;
            state.lastGenerateSummary = summary;
            String input = state.parentInput();
            state.start(runNo, outputPrefix, FULL_NODES, RegenerateScope.ALL, List.of(), null, 0,
                    new Mishaps(input.contains(FAIL_MARKER), input.contains(OUTPUT_REVISE_MARKER),
                            input.contains(PAGE_FAIL_MARKER)), clock.instant());
        }
    }

    @Override
    public AiGenerationStatus status(String threadId) {
        TransactionGuard.requireNoTransaction();
        statusCalls.computeIfAbsent(threadId, id -> new AtomicInteger()).incrementAndGet();
        throwIfFailing(false);
        ThreadState state = existing(threadId);
        synchronized (state) {
            advance(state);
            return new AiGenerationStatus(state.runState, state.runNo, state.node,
                    state.node == null ? null : STAGES.get(state.node), state.error);
        }
    }

    @Override
    public AiGenerationResult result(String threadId) {
        TransactionGuard.requireNoTransaction();
        throwIfFailing(false);
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
            state.start(runNo, outputPrefix, nodes, scope, pages, base, ++state.regenerations, Mishaps.NONE,
                    clock.instant());
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
            // 같은 범위를 처음부터 다시. 실패 흉내는 그림 실패만 이어 간다
            state.start(runNo, outputPrefix, state.nodes, state.scope, state.pages, state.base, state.titleNo,
                    new Mishaps(false, false, state.mishaps.pageFail()), clock.instant());
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
        this.downNext.set(false);
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
     * 테스트용. 마지막 generate에 받은 context (생성 전 확인에서 고친 입력이 들어 있다).
     */
    public Map<String, Object> lastGenerateContext(String threadId) {
        ThreadState state = threads.get(threadId);
        return state == null ? null : state.lastGenerateContext;
    }

    /**
     * 테스트용. 마지막 generate에 받은 "이번 이야기에서 나누고 싶은 것".
     */
    public String lastGenerateSummary(String threadId) {
        ThreadState state = threads.get(threadId);
        return state == null ? null : state.lastGenerateSummary;
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
        throwIfFailing(true);
    }

    /**
     * @param userCall false면 스케줄러가 부르는 생성 진행 확인이라 [[AI_DOWN]]의 한 번을 쓰지 않는다
     */
    private void throwIfFailing(boolean userCall) {
        BusinessException current = failure;
        if (current != null) {
            throw new BusinessException(current.getErrorCode());
        }
        if (userCall && downNext.compareAndSet(true, false)) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    // local에서 진짜 AI의 응답 시간을 흉내 낸다 (test는 0)
    private static void pause(Duration delay) {
        if (!delay.isPositive()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int followUps(String threadId) {
        if (settings.turns() > 0) {
            return settings.turns();
        }
        return Math.floorMod(threadId.hashCode(), Fake.MAX_QUESTIONS);
    }

    /**
     * 이번 status에서 도달할 노드까지 나아간다. 노드를 다 지나면 결과를 만들고 REVIEWING.
     */
    private void advance(ThreadState state) {
        if (state.runState != AiRunState.RUNNING) {
            return;
        }
        long reach = settings.timedStages()
                ? Duration.between(state.startedAt, clock.instant()).dividedBy(settings.stageDuration()) + 1
                : state.step + 1;
        while (state.step < Math.min(reach, state.nodes.size())) {
            String node = state.nodes.get(state.step++);
            state.node = node;
            if (state.mishaps.failAtWriteStory() && node.equals(FAIL_AT)) {
                state.runState = AiRunState.FAILED;
                state.error = FAILURE_CODE;
                return;
            }
        }
        if (reach > state.nodes.size()) {
            state.result = buildResult(state);
            state.byCheckpoint.put(state.result.checkpointId(), state.result);
            state.runState = AiRunState.REVIEWING;
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
            String text = previous != null ? previous.text() : SAMPLE.pageText(pageNo);
            if (!redraw) {
                pages.add(new AiGenerationResult.Page(pageNo, text, previous.imagePrompt(), previous.imagePath(),
                        previous.imageStatus()));
            } else if (state.mishaps.pageFail() && pageNo == FAILED_PAGE) {
                pages.add(new AiGenerationResult.Page(pageNo, text, "page " + pageNo, null, "failed"));
            } else {
                String key = prefix + "images/page-%02d.png".formatted(pageNo);
                storage.write(key, png(color(pageNo, runNo)), "image/png");
                pages.add(new AiGenerationResult.Page(pageNo, text, "page " + pageNo, key, "done"));
            }
        }

        List<AiGenerationResult.CharacterSheet> sheets;
        if (base != null) {
            sheets = base.characterSheets();
        } else {
            String sheetKey = prefix + "images/sheet-main-1.png";
            storage.write(sheetKey, png(Color.GRAY), "image/png");
            sheets = List.of(new AiGenerationResult.CharacterSheet("main", "아로", sheetKey));
        }

        SafetyResult safety = state.mishaps.unsafeOutput()
                ? new SafetyResult(SafetyLevel.REVISE, "harmful_discipline", "완성된 이야기에 아이에게 맞지 않는 표현이 있어요.")
                : SafetyResult.ok();
        return new AiGenerationResult(runNo, checkpointId(runNo), SAMPLE.title(state.titleNo),
                Map.of("hero", "아로", "run_no", runNo), sheets, pages, SAMPLE.resultGuide(),
                new AiGenerationResult.Cost(INPUT_TOKENS, OUTPUT_TOKENS, pageCount + 1), safety);
    }

    private Color color(int pageNo, int runNo) {
        return Color.getHSBColor((pageNo - 1) / (float) pageCount, 0.45f + 0.1f * (runNo % 5), 0.95f);
    }

    // 글꼴 없이 그린다 (서버에 글꼴이 없어도 되게). 바탕색 위에 밝은 원 하나
    private static byte[] png(Color color) {
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
        graphics.setColor(color.brighter());
        graphics.fillOval(IMAGE_SIZE / 4, IMAGE_SIZE / 4, IMAGE_SIZE / 2, IMAGE_SIZE / 2);
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

    /**
     * fake-ai/sample-story.json. 쪽 글에는 **의성어** 표시가 그대로 있다.
     *
     * @param guide AI 결과의 guide와 같은 구조 (04-guide.json)
     */
    public record Sample(String title, List<String> pages, List<String> questions, Map<String, Object> guide) {

        public Sample {
            pages = List.copyOf(pages);
            questions = List.copyOf(questions);
            if (questions.size() < Fake.MAX_QUESTIONS) {
                throw new IllegalStateException("가짜 AI 샘플의 질문은 " + Fake.MAX_QUESTIONS + "개가 필요해요");
            }
        }

        static Sample load() {
            try (InputStream in = FakeAiClient.class.getResourceAsStream(SAMPLE_PATH)) {
                if (in == null) {
                    throw new IllegalStateException("가짜 AI 샘플이 없어요: " + SAMPLE_PATH);
                }
                return JsonMapper.builder().build().readValue(in, Sample.class);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * @param regenerations 0이면 첫 생성, n이면 n번째 다시 만들기
         */
        public String title(int regenerations) {
            return regenerations == 0 ? title : title + " (다시 " + regenerations + ")";
        }

        /**
         * 샘플보다 쪽이 많으면 처음부터 다시 쓴다.
         */
        public String pageText(int pageNo) {
            return pages.get((pageNo - 1) % pages.size());
        }

        public AiGenerationResult.Guide resultGuide() {
            return HttpAiClient.ResultBody.guide(guide);
        }
    }

    /**
     * 실행 하나에 흉내 낼 실패.
     *
     * @param failAtWriteStory [[FAIL]]: write_story에서 FAILED
     * @param unsafeOutput     [[OUTPUT_REVISE]]: 결과 safety가 REVISE
     * @param pageFail         [[PAGE_FAIL]]: FAILED_PAGE쪽 그림만 failed
     */
    private record Mishaps(boolean failAtWriteStory, boolean unsafeOutput, boolean pageFail) {

        private static final Mishaps NONE = new Mishaps(false, false, false);
    }

    private static final class ThreadState {

        private final Map<String, Object> context;
        // 첫 질문 뒤에 더 묻는 질문 수
        private final int followUps;
        private final List<String> answers = new CopyOnWriteArrayList<>();
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
        private Mishaps mishaps = Mishaps.NONE;
        private Instant startedAt;
        private int regenerations;
        // 결과 제목의 (다시 n). 다시 시도는 실패한 실행의 n을 이어 간다
        private int titleNo;
        private String lastBaseCheckpointId;
        private Map<String, Object> lastGenerateContext;
        private String lastGenerateSummary;
        private RegenerateScope lastRegenerateScope;

        private ThreadState(Map<String, Object> context, int followUps) {
            this.context = new LinkedHashMap<>(context);
            this.followUps = followUps;
        }

        private void start(int runNo, String outputPrefix, List<String> nodes, RegenerateScope scope,
                           List<Integer> pages, AiGenerationResult base, int titleNo, Mishaps mishaps,
                           Instant startedAt) {
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
            this.titleNo = titleNo;
            this.mishaps = mishaps;
            this.startedAt = startedAt;
        }

        // 실패 흉내 표시를 찾는 곳: 전하고 싶은 말과 인터뷰 답변
        private String parentInput() {
            StringBuilder input = new StringBuilder();
            if (context.get("parent") instanceof Map<?, ?> parent && parent.get("message") instanceof String message) {
                input.append(message);
            }
            answers.forEach(answer -> input.append('\n').append(answer));
            return input.toString();
        }
    }
}
