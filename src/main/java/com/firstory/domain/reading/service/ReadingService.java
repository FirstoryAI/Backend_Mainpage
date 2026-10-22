package com.firstory.domain.reading.service;

import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.generation.repository.StoryPageRepository;
import com.firstory.domain.guide.entity.GuidePrompt;
import com.firstory.domain.guide.repository.GuidePromptRepository;
import com.firstory.domain.guide.service.GuideService;
import com.firstory.domain.reading.dto.BookResponse;
import com.firstory.domain.reading.dto.ReadingSessionResponse;
import com.firstory.domain.reading.entity.DialogueAction;
import com.firstory.domain.reading.entity.DialogueActionType;
import com.firstory.domain.reading.entity.ReadingSession;
import com.firstory.domain.reading.repository.DialogueActionRepository;
import com.firstory.domain.reading.repository.ReadingSessionRepository;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.service.StoryLoader;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * 함께 읽기 (RD-01~03). 확정된(COMPLETED) 동화만 읽는다.
 * <p>
 * 세션 id로 들어오는 API(쪽 넘김, 대화 버튼, 끝)는 세션 → 동화 → 회원까지 확인한다. 다른 회원의 세션이면 404.
 * 대화 질문은 그 세션 버전의 가이드에 속할 때만 받는다. 같은 질문에 다시 누르면 마지막 값으로 바꾼다.
 * 끝(complete)은 두 번 불러도 안전하고, 동화의 처음 읽은 시각은 처음 한 번만 남는다.
 */
@Service
public class ReadingService {

    private final StoryLoader loader;
    private final StoryPageRepository pageRepository;
    private final GuidePromptRepository promptRepository;
    private final ReadingSessionRepository sessionRepository;
    private final DialogueActionRepository dialogueActionRepository;
    private final Clock clock;
    private final int pageCount;

    public ReadingService(StoryLoader loader, StoryPageRepository pageRepository,
                          GuidePromptRepository promptRepository, ReadingSessionRepository sessionRepository,
                          DialogueActionRepository dialogueActionRepository, Clock clock,
                          FirstoryProperties properties) {
        this.loader = loader;
        this.pageRepository = pageRepository;
        this.promptRepository = promptRepository;
        this.sessionRepository = sessionRepository;
        this.dialogueActionRepository = dialogueActionRepository;
        this.clock = clock;
        this.pageCount = properties.story().pageCount();
    }

    @Transactional(readOnly = true)
    public BookResponse book(Long userId, Long storyId) {
        Story story = GuideService.completedStory(loader, userId, storyId);
        StoryVersion version = story.getCurrentVersion();
        return BookResponse.of(story, version, pageCount,
                pageRepository.findAllByVersionIdOrderByPageNoAsc(version.getId()),
                promptRepository.findAllByVersionId(version.getId()));
    }

    @Transactional
    public ReadingSessionResponse start(Long userId, Long storyId) {
        Story story = GuideService.completedStory(loader, userId, storyId);
        return ReadingSessionResponse.of(sessionRepository.save(
                ReadingSession.start(story, story.getCurrentVersion(), clock.instant())));
    }

    /**
     * @return 가장 최근의 끝나지 않은 세션. 없으면 empty
     */
    @Transactional(readOnly = true)
    public Optional<ReadingSessionResponse> resume(Long userId, Long storyId) {
        Story story = GuideService.completedStory(loader, userId, storyId);
        return sessionRepository.findTopByStoryIdAndCompletedAtIsNullOrderByStartedAtDesc(story.getId())
                .map(ReadingSessionResponse::of);
    }

    @Transactional
    public void moveTo(Long userId, Long sessionId, int page) {
        ReadingSession session = ownedSession(userId, sessionId);
        if (session.isCompleted()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        session.moveTo(page, pageCount);
    }

    /**
     * RD-02 대화 버튼. North Star(대화 발생)는 TALKED 수로 센다.
     */
    @Transactional
    public void recordDialogue(Long userId, Long sessionId, Long promptId, DialogueActionType action) {
        ReadingSession session = ownedSession(userId, sessionId);
        GuidePrompt prompt = promptRepository.findInVersion(promptId, session.getVersion().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROMPT_NOT_FOUND));
        dialogueActionRepository.findBySessionIdAndPromptId(session.getId(), prompt.getId())
                .ifPresentOrElse(existing -> existing.change(action),
                        () -> dialogueActionRepository.save(DialogueAction.record(session, prompt, action)));
    }

    /**
     * RD-03 끝. 이미 끝난 세션이면 그대로 둔다.
     */
    @Transactional
    public void complete(Long userId, Long sessionId) {
        ReadingSession session = ownedSession(userId, sessionId);
        session.complete(clock.instant());
        session.getStory().markFirstReadCompleted(clock.instant());
    }

    // 세션을 잠가서 같은 세션에 동시에 들어온 요청을 차례로 처리한다
    private ReadingSession ownedSession(Long userId, Long sessionId) {
        ReadingSession session = sessionRepository.findOwnedForUpdate(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.READING_SESSION_NOT_FOUND));
        if (session.getStory().getStatus() != StoryStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        return session;
    }
}
