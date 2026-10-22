package com.firstory.domain.generation.service;

import com.firstory.domain.generation.dto.GenerationStatusResponse;
import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.domain.generation.entity.RegenerateScope;
import com.firstory.domain.generation.entity.RunTrigger;
import com.firstory.domain.generation.entity.StoryFiles;
import com.firstory.domain.generation.repository.GenerationRunRepository;
import com.firstory.domain.notification.entity.NotificationType;
import com.firstory.domain.notification.service.NotificationService;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryInput;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.entity.Tone;
import com.firstory.domain.story.service.StoryLoader;
import com.firstory.global.ai.AiClient;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 생성 시작(CRT-06)과 진행 조회(GEN-01).
 * AI를 부르는 메서드는 ① 조회·검증 → ② 트랜잭션 밖에서 AI 호출 → ③ 새 트랜잭션으로 저장.
 * 진행은 GenerationPoller가 AI에 물어 DB에 저장하고, 진행 조회는 DB만 읽는다.
 */
@Service
@RequiredArgsConstructor
public class GenerationService {

    private final StoryLoader loader;
    private final GenerationRunRepository runRepository;
    private final AiClient aiClient;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 생성 전 확인(CRT-06)의 "만들기". 민감 주제가 감지됐으면 분위기를 먼저 골라야 한다.
     * finalContext는 AI 쪽 thread에 이미 있으므로 tone만 보낸다.
     */
    public GenerationStatusResponse confirm(Long userId, Long storyId) {
        // ①
        Prepared prepared = transactionTemplate.execute(status -> {
            Story story = loader.findEditable(userId, storyId, EnumSet.of(StoryStatus.CONFIRMING));
            StoryInput input = loader.input(story);
            if (input.isToneRequired() && input.getTone() == null) {
                throw new BusinessException(ErrorCode.TONE_REQUIRED);
            }
            return new Prepared(story, nextRunNo(story), input.getTone());
        });
        Story story = prepared.story();
        UUID runId = UUID.randomUUID();
        // ②
        aiClient.generate(story.getThreadId(), runId, prepared.runNo(),
                StoryFiles.runPrefix(story.getId(), prepared.runNo()), prepared.tone());
        // ③
        Started started = transactionTemplate.execute(status -> {
            Story locked = loader.lockUnchanged(story);
            GenerationRun run = runRepository.save(GenerationRun.start(locked, prepared.runNo(), RunTrigger.INITIAL,
                    RegenerateScope.ALL, null, List.of(), null, runId.toString(), clock.instant()));
            locked.changeStatus(StoryStatus.REQUESTED);
            return new Started(run.getId(), GenerationStatusResponse.of(locked, run));
        });
        // 트랜잭션 밖에서 문자 알림
        notificationService.notify(started.runId(), NotificationType.STORY_STARTED);
        return started.response();
    }

    /**
     * 진행 조회. 프론트가 3초마다 불러도 AI에 부담이 없도록 DB만 읽는다.
     */
    @Transactional(readOnly = true)
    public GenerationStatusResponse getGeneration(Long userId, Long storyId) {
        Story story = loader.findOwned(userId, storyId);
        return GenerationStatusResponse.of(story, runRepository.findTopByStoryIdOrderByRunNoDesc(story.getId())
                .orElse(null));
    }

    int nextRunNo(Story story) {
        return runRepository.findTopByStoryIdOrderByRunNoDesc(story.getId()).map(GenerationRun::getRunNo).orElse(0) + 1;
    }

    private record Prepared(Story story, int runNo, Tone tone) {
    }

    private record Started(Long runId, GenerationStatusResponse response) {
    }
}
