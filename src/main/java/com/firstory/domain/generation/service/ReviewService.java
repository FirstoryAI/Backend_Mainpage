package com.firstory.domain.generation.service;

import com.firstory.domain.generation.dto.ApproveResponse;
import com.firstory.domain.generation.dto.GenerationStatusResponse;
import com.firstory.domain.generation.dto.PreviewResponse;
import com.firstory.domain.generation.dto.RegenerateRequest;
import com.firstory.domain.generation.dto.VersionResponse;
import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.domain.generation.entity.RegenerateScope;
import com.firstory.domain.generation.entity.RunStatus;
import com.firstory.domain.generation.entity.RunTrigger;
import com.firstory.domain.generation.entity.StoryFiles;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.generation.repository.GenerationRunRepository;
import com.firstory.domain.generation.repository.StoryPageRepository;
import com.firstory.domain.generation.repository.StoryVersionRepository;
import com.firstory.domain.safety.entity.SafetyOutcome;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.safety.service.SafetyGuard;
import com.firstory.domain.story.dto.StoryStepResponse;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.service.StoryLoader;
import com.firstory.global.ai.AiClient;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.entity.CustomText;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 부모 검토 (REV-01~04): 미리보기, 다시 만들기, 버전 목록과 전환, 확정, 실패 후 다시 시도.
 * <p>
 * 버전은 지우지 않는다. 버전 전환은 Story.currentVersion만 바꾸고 AI를 부르지 않으며 다시 만들기 횟수에 넣지 않는다.
 * 다시 시도도 다시 만들기 횟수에 넣지 않는다.
 * AI를 부르는 메서드는 ① 조회·검증 → ② 트랜잭션 밖에서 AI 호출 → ③ 새 트랜잭션으로 저장. AI가 실패하면 ③을 하지 않는다.
 */
@Slf4j
@Service
public class ReviewService {

    static final int FEEDBACK_MAX_LENGTH = 300;

    private static final Set<StoryStatus> REVIEWING = EnumSet.of(StoryStatus.REVIEWING);
    private static final Set<StoryStatus> PREVIEWABLE = EnumSet.of(StoryStatus.REVIEWING, StoryStatus.COMPLETED);

    private final StoryLoader loader;
    private final GenerationRunRepository runRepository;
    private final StoryVersionRepository versionRepository;
    private final StoryPageRepository pageRepository;
    private final GenerationService generationService;
    private final SafetyGuard safetyGuard;
    private final AiClient aiClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int maxRegenerations;
    private final int pageCount;

    public ReviewService(StoryLoader loader, GenerationRunRepository runRepository,
                         StoryVersionRepository versionRepository, StoryPageRepository pageRepository,
                         GenerationService generationService, SafetyGuard safetyGuard, AiClient aiClient,
                         TransactionTemplate transactionTemplate, Clock clock, FirstoryProperties properties) {
        this.loader = loader;
        this.runRepository = runRepository;
        this.versionRepository = versionRepository;
        this.pageRepository = pageRepository;
        this.generationService = generationService;
        this.safetyGuard = safetyGuard;
        this.aiClient = aiClient;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.maxRegenerations = properties.policy().maxRegenerations();
        this.pageCount = properties.story().pageCount();
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(Long userId, Long storyId) {
        Story story = loader.findEditable(userId, storyId, PREVIEWABLE);
        return previewOf(story);
    }

    /**
     * 다시 만들기 (REV-02). 메모가 있으면 위기 신호를 먼저 검사한다. 다시 쓰기 안내면 다시 만들지 않고 횟수도 그대로다.
     */
    public StoryStepResponse regenerate(Long userId, Long storyId, RegenerateRequest request) {
        RegenerateScope scope = request.scope();
        List<Integer> pages = targetPages(scope, request.pages());
        String feedback = feedback(request.feedback());

        // ①
        Prepared prepared = transactionTemplate.execute(status -> {
            Story story = loader.findEditable(userId, storyId, REVIEWING);
            if (story.getRegenerateCount() >= maxRegenerations) {
                throw new BusinessException(ErrorCode.REGENERATION_LIMIT_REACHED);
            }
            StoryVersion current = currentVersion(story);
            return new Prepared(story, current.getId(), current.getCheckpointId(), generationService.nextRunNo(story),
                    loader.input(story).isToneRequired());
        });
        Story story = prepared.story();

        // ② 메모의 위기 신호 (기록과 차단은 SafetyGuard가 자기 트랜잭션에서 한다)
        SafetyOutcome outcome = null;
        if (feedback != null) {
            outcome = safetyGuard.check(story, SafetySource.REGEN_FEEDBACK, List.of(feedback), null, null);
            if (!outcome.canContinue()) {
                StoryStatus status = outcome.isBlocked() ? StoryStatus.BLOCKED : story.getStatus();
                return StoryStepResponse.of(storyId, status, false, outcome, prepared.toneRequired());
            }
        }

        // ③ AI가 실패하면 실행도 횟수도 저장하지 않는다
        UUID runId = UUID.randomUUID();
        aiClient.regenerate(story.getThreadId(), scope, pages, feedback, prepared.baseCheckpointId(), runId,
                prepared.runNo(), StoryFiles.runPrefix(story.getId(), prepared.runNo()));

        // ④
        SafetyOutcome checked = outcome;
        return transactionTemplate.execute(status -> {
            Story locked = loader.lockUnchanged(story);
            StoryVersion base = currentVersion(locked);
            if (!base.getId().equals(prepared.baseVersionId())) {
                throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
            }
            locked.requestRegeneration(maxRegenerations);
            runRepository.save(GenerationRun.start(locked, prepared.runNo(), RunTrigger.REGENERATE, scope, base, pages,
                    feedback, runId.toString(), clock.instant()));
            return StoryStepResponse.of(locked.getId(), locked.getStatus(), true, checked,
                    loader.input(locked).isToneRequired());
        });
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> versions(Long userId, Long storyId) {
        Story story = loader.findOwned(userId, storyId);
        Long currentId = story.getCurrentVersion() == null ? null : story.getCurrentVersion().getId();
        return versionRepository.findAllByStoryIdOrderByVersionNoAsc(story.getId()).stream()
                .map(version -> VersionResponse.of(version, currentId))
                .toList();
    }

    /**
     * 버전 전환 (되돌리기, 앞으로 가기). AI를 부르지 않고 다시 만들기 횟수도 바꾸지 않는다.
     */
    @Transactional
    public PreviewResponse showVersion(Long userId, Long storyId, Long versionId) {
        Story story = loader.findEditable(userId, storyId, REVIEWING);
        StoryVersion version = versionRepository.findByIdAndStoryId(versionId, story.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSION_NOT_FOUND));
        story.showVersion(version);
        return previewOf(story);
    }

    /**
     * 확정 (REV-04). Spring이 기준이라 먼저 저장한다. AI 쪽 정리(approve)가 실패해도 확정은 그대로 둔다.
     * 확정 때는 알림을 보내지 않는다 (PRD 33).
     */
    public ApproveResponse approve(Long userId, Long storyId) {
        Story story = transactionTemplate.execute(status -> {
            Story found = loader.findEditable(userId, storyId, REVIEWING);
            found.confirm(clock.instant());
            return found;
        });
        try {
            aiClient.approve(story.getThreadId());
        } catch (RuntimeException e) {
            log.warn("AI 쪽 확정 정리에 실패했어요. 동화 확정은 그대로 둬요: story_id={}, cause={}", storyId,
                    e instanceof BusinessException be ? be.getErrorCode() : e.getClass().getSimpleName());
        }
        return new ApproveResponse(story.getId(), story.getStatus(), story.getConfirmedAt());
    }

    /**
     * 실패 후 다시 시도 (ST-03). 마지막 실패 실행과 같은 범위, 쪽, 기준 버전으로 새 실행(RETRY)을 만든다.
     * 다시 만들기 횟수에 넣지 않는다.
     * <p>
     * 결과 위기 신호(OUTPUT_UNSAFE)로 실패한 경우 AI 그래프는 부모 검토(parent_review)에서 멈춰 있어 retry가 409다.
     * 그래서 AiClient.regenerate를 부른다: 실패한 실행의 범위(첫 생성 ALL이면 CHARACTER_STORY)와 쪽,
     * 메모 없음, 기준은 실패한 실행의 기준 버전 checkpointId (첫 생성이라 없으면 null, AI는 현재 상태에서 다시 만든다).
     * 그 외 실패(AI 오류, TIMEOUT 등)는 AiClient.retry.
     */
    public GenerationStatusResponse retry(Long userId, Long storyId) {
        // ①
        RetryPlan plan = transactionTemplate.execute(status -> {
            Story story = loader.findEditable(userId, storyId, EnumSet.of(StoryStatus.FAILED));
            GenerationRun last = runRepository.findTopByStoryIdOrderByRunNoDesc(story.getId())
                    .filter(run -> run.getStatus() == RunStatus.FAILED)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION));
            StoryVersion base = last.getBaseVersion();
            return new RetryPlan(story, last.getRunNo() + 1, last.getScope(), List.copyOf(last.getTargetPages()),
                    base == null ? null : base.getId(), base == null ? null : base.getCheckpointId(),
                    last.getFeedback(), GenerationResultRecorder.OUTPUT_UNSAFE.equals(last.getErrorCode()));
        });
        Story story = plan.story();
        UUID runId = UUID.randomUUID();
        String outputPrefix = StoryFiles.runPrefix(story.getId(), plan.runNo());
        // ②
        if (plan.afterUnsafeOutput()) {
            RegenerateScope scope = plan.scope() == RegenerateScope.ALL ? RegenerateScope.CHARACTER_STORY : plan.scope();
            aiClient.regenerate(story.getThreadId(), scope, plan.targetPages(), null, plan.baseCheckpointId(), runId,
                    plan.runNo(), outputPrefix);
        } else {
            aiClient.retry(story.getThreadId(), runId, plan.runNo(), outputPrefix);
        }
        // ③
        return transactionTemplate.execute(status -> {
            Story locked = loader.lockUnchanged(story);
            StoryVersion base = plan.baseVersionId() == null ? null
                    : versionRepository.getReferenceById(plan.baseVersionId());
            GenerationRun run = runRepository.save(GenerationRun.start(locked, plan.runNo(), RunTrigger.RETRY,
                    plan.scope(), base, plan.targetPages(), plan.feedback(), runId.toString(), clock.instant()));
            locked.retry();
            return GenerationStatusResponse.of(locked, run);
        });
    }

    private PreviewResponse previewOf(Story story) {
        StoryVersion version = currentVersion(story);
        return PreviewResponse.of(story, version, pageRepository.findAllByVersionIdOrderByPageNoAsc(version.getId()),
                Math.max(0, maxRegenerations - story.getRegenerateCount()));
    }

    private static StoryVersion currentVersion(Story story) {
        StoryVersion version = story.getCurrentVersion();
        if (version == null) {
            throw new BusinessException(ErrorCode.VERSION_NOT_FOUND);
        }
        return version;
    }

    /**
     * IMAGES는 1~page-count 안의 중복 없는 쪽(비면 전체). 다른 범위에 쪽을 보내면 INVALID_PAGE_SELECTION.
     */
    private List<Integer> targetPages(RegenerateScope scope, List<Integer> pages) {
        if (scope == RegenerateScope.ALL) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        List<Integer> requested = pages == null ? List.of() : pages;
        if (scope != RegenerateScope.IMAGES) {
            if (!requested.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PAGE_SELECTION);
            }
            return List.of();
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer page : requested) {
            if (page == null || page < 1 || page > pageCount || !seen.add(page)) {
                throw new BusinessException(ErrorCode.INVALID_PAGE_SELECTION);
            }
        }
        return requested.stream().filter(Objects::nonNull).sorted().toList();
    }

    private static String feedback(String raw) {
        String feedback = CustomText.strip(raw);
        if (feedback != null && feedback.codePointCount(0, feedback.length()) > FEEDBACK_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.FEEDBACK_TOO_LONG);
        }
        return feedback;
    }

    private record Prepared(Story story, Long baseVersionId, String baseCheckpointId, int runNo,
                            boolean toneRequired) {
    }

    /**
     * @param afterUnsafeOutput 마지막 실패가 결과 위기 신호(OUTPUT_UNSAFE)인지
     */
    private record RetryPlan(Story story, int runNo, RegenerateScope scope, List<Integer> targetPages,
                             Long baseVersionId, String baseCheckpointId, String feedback,
                             boolean afterUnsafeOutput) {
    }
}
