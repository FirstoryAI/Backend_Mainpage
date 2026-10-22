package com.firstory.domain.generation.service;

import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.domain.generation.entity.GenerationStage;
import com.firstory.domain.generation.repository.GenerationRunRepository;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.global.ai.AiClient;
import com.firstory.global.ai.AiGenerationStatus;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 실행 중(RUNNING)인 생성의 진행을 AI에 물어 DB에 저장한다. 스케줄러(GenerationPollScheduler)가 주기적으로 부르고,
 * 테스트는 pollOnce()를 직접 부른다.
 *
 * <pre>
 * AI RUNNING          run.progress(node), 스토리 GENERATING_TEXT(1~2단계) / GENERATING_IMAGES(3~4단계)
 * AI REVIEWING        result를 받아 저장 (GenerationResultRecorder)
 * AI FAILED           실행 FAILED, 스토리 FAILED
 * 시작 후 timeout     실행 FAILED(TIMEOUT), 스토리 FAILED
 * AI 연결 실패        아무것도 바꾸지 않고 다음 주기에 다시 (결국 timeout이 처리)
 * </pre>
 * 실행을 잡을 때는 FOR UPDATE SKIP LOCKED로 짧게 잡는다. AI 호출은 트랜잭션 밖에서 하고,
 * 저장할 때 실행을 다시 잠가 아직 RUNNING인지 확인한다. 그래서 서버가 여러 대여도 같은 실행을 두 번 저장하지 않는다.
 */
@Slf4j
@Component
public class GenerationPoller {

    static final int BATCH_SIZE = 20;
    static final String TIMEOUT = "TIMEOUT";
    static final String AI_FAILED = "AI_FAILED";

    private final GenerationRunRepository runRepository;
    private final AiClient aiClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration timeout;

    public GenerationPoller(GenerationRunRepository runRepository, AiClient aiClient,
                            TransactionTemplate transactionTemplate, Clock clock, FirstoryProperties properties) {
        this.runRepository = runRepository;
        this.aiClient = aiClient;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.timeout = properties.generation().timeout();
    }

    /**
     * @return 이번에 확인한 실행 수
     */
    public int pollOnce() {
        List<Long> runIds = transactionTemplate.execute(status ->
                runRepository.lockRunning(BATCH_SIZE).stream().map(GenerationRun::getId).toList());
        for (Long runId : runIds) {
            // 실행 하나의 실패가 다른 실행을 막지 않게 한다
            try {
                poll(runId);
            } catch (RuntimeException e) {
                log.warn("생성 진행을 확인하지 못했어요: run_id={}, cause={}", runId, e.getClass().getSimpleName());
            }
        }
        return runIds.size();
    }

    private void poll(Long runId) {
        RunRef run = transactionTemplate.execute(status -> runRepository.findById(runId)
                .filter(GenerationRun::isRunning)
                .map(found -> new RunRef(found.getId(), found.getStory().getThreadId(), found.getStartedAt()))
                .orElse(null));
        if (run == null) {
            return;
        }
        if (clock.instant().isAfter(run.startedAt().plus(timeout))) {
            fail(runId, TIMEOUT);
            return;
        }
        AiGenerationStatus status;
        try {
            status = aiClient.status(run.threadId());
        } catch (BusinessException e) {
            log.info("AI 진행 상태를 받지 못해 다음에 다시 확인해요: run_id={}, code={}", runId, e.getErrorCode());
            return;
        }
        switch (status.state()) {
            case RUNNING -> progress(runId, status.currentNode());
            case REVIEWING -> record(runId, run.threadId());
            case FAILED -> fail(runId, status.error() == null ? AI_FAILED : status.error());
            default -> log.warn("생성 중에 예상하지 못한 AI 상태예요: run_id={}, state={}", runId, status.state());
        }
    }

    private void progress(Long runId, String node) {
        GenerationStage stage = GenerationStage.ofNode(node).orElse(null);
        if (stage == null) {
            // 모르는 노드는 지금 단계를 그대로 둔다
            return;
        }
        transactionTemplate.executeWithoutResult(status -> runRepository.findByIdForUpdate(runId)
                .filter(GenerationRun::isRunning)
                .ifPresent(run -> {
                    run.progress(node);
                    run.getStory().advanceGenerationTo(stage.isText()
                            ? StoryStatus.GENERATING_TEXT : StoryStatus.GENERATING_IMAGES);
                }));
    }

    // TODO: 4장 결과 저장 (GenerationResultRecorder)
    private void record(Long runId, String threadId) {
    }

    /**
     * @return 이번에 실패로 바꿨으면 true (이미 다른 쪽이 처리했으면 false)
     */
    boolean fail(Long runId, String errorCode) {
        Boolean failed = transactionTemplate.execute(status -> runRepository.findByIdForUpdate(runId)
                .filter(GenerationRun::isRunning)
                .map(run -> {
                    run.fail(errorCode, clock.instant());
                    Story story = run.getStory();
                    story.changeStatus(StoryStatus.FAILED);
                    return true;
                })
                .orElse(false));
        if (Boolean.TRUE.equals(failed)) {
            log.info("생성이 실패했어요: run_id={}, code={}", runId, errorCode);
        }
        return Boolean.TRUE.equals(failed);
    }

    private record RunRef(Long id, String threadId, Instant startedAt) {
    }
}
