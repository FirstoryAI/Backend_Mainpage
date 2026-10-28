package com.firstory.domain.story.service;

import com.firstory.domain.guide.entity.ReadingGuide;
import com.firstory.domain.guide.repository.ReadingGuideRepository;
import com.firstory.domain.reading.entity.ReadingSession;
import com.firstory.domain.reading.repository.ReadingSessionRepository;
import com.firstory.domain.story.dto.HomeResponse;
import com.firstory.domain.story.dto.LibraryResponse;
import com.firstory.domain.story.dto.StoryCardResponse;
import com.firstory.domain.story.dto.StoryDetailResponse;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryInput;
import com.firstory.domain.story.entity.LibraryFilter;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.repository.StoryInputRepository;
import com.firstory.domain.story.repository.StoryRepository;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 서재(LIB-01)와 홈(HOME-01). BLOCKED 동화는 서재와 홈 어디에도 보이지 않는다.
 * 목록은 현재 버전과 아이를 페치 조인하고, 상황 분류는 목록의 동화를 한 번에 읽어서 목록 크기와 무관하게 쿼리 수가 일정하다.
 */
@Service
@RequiredArgsConstructor
public class LibraryService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    static final int HOME_SIZE = 3;

    private static final Sort RECENTLY_UPDATED = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
    private static final Set<StoryStatus> IN_PROGRESS = EnumSet.of(StoryStatus.DRAFT, StoryStatus.INTERVIEWING,
            StoryStatus.CONFIRMING, StoryStatus.REQUESTED, StoryStatus.GENERATING_TEXT, StoryStatus.GENERATING_IMAGES,
            StoryStatus.REVIEWING, StoryStatus.FAILED);

    private final StoryRepository storyRepository;
    private final StoryInputRepository storyInputRepository;
    private final ReadingSessionRepository readingSessionRepository;
    private final ReadingGuideRepository guideRepository;
    private final UserRepository userRepository;
    private final FirstoryProperties properties;

    @Transactional(readOnly = true)
    public LibraryResponse library(Long userId, LibraryFilter filter, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Page<Story> stories = storyRepository.findLibrary(userId, filter.getStatuses(), filter.onlyRead(),
                filter.onlyUnread(), PageRequest.of(page, size, RECENTLY_UPDATED));
        return LibraryResponse.of(stories, cards(stories.getContent()));
    }

    @Transactional(readOnly = true)
    public HomeResponse home(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        List<StoryCardResponse> inProgress = cards(storyRepository.findLibrary(userId, IN_PROGRESS, false, false,
                PageRequest.of(0, HOME_SIZE, RECENTLY_UPDATED)).getContent());
        List<StoryCardResponse> recent = cards(storyRepository.findRecentCompleted(userId, PageRequest.of(0, HOME_SIZE)));
        return new HomeResponse(user.hasSeenOnboarding(), inProgress, recent, counts(userId));
    }

    /**
     * 카드 목록. 상황 분류는 StoryInput에 있어 목록의 동화 id로 한 번에 읽는다 (Story에서 StoryInput으로 가는 연관이 없다).
     */
    private List<StoryCardResponse> cards(List<Story> stories) {
        Map<Long, String> categories = storyInputRepository.findAllById(stories.stream().map(Story::getId).toList())
                .stream().filter(input -> input.categoryLabel() != null)
                .collect(Collectors.toMap(StoryInput::getStoryId, StoryInput::categoryLabel));
        return stories.stream().map(story -> StoryCardResponse.of(story, categories.get(story.getId()))).toList();
    }

    /**
     * 동화 상세 (LIB-02). 차단된 동화도 상태 그대로 보여준다. 다른 회원의 동화와 지운 동화는 STORY_NOT_FOUND.
     */
    @Transactional(readOnly = true)
    public StoryDetailResponse detail(Long userId, Long storyId) {
        Story story = storyRepository.findDetail(storyId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
        StoryInput input = storyInputRepository.findById(story.getId()).orElse(null);
        StoryDetailResponse.Situation situation = input == null || !input.hasSituation() ? null
                : new StoryDetailResponse.Situation(input.getSituationCategory(),
                input.getSituationCategory().getLabel(), input.getSituationTiming());
        Instant lastReadAt = readingSessionRepository.findTopByStoryIdOrderByStartedAtDesc(story.getId())
                .map(ReadingSession::getStartedAt).orElse(null);
        boolean hasVersion = story.getCurrentVersion() != null;
        String synopsis = !hasVersion ? null : guideRepository.findByVersionId(story.getCurrentVersion().getId())
                .map(ReadingGuide::getSynopsis).orElse(null);
        return new StoryDetailResponse(story.getId(), hasVersion ? story.getTitle() : null, story.getStatus(),
                story.libraryLabel(), StoryCardResponse.coverUrl(story), StoryCardResponse.displayName(story.getChild()),
                situation, input == null ? null : input.getInterviewSummary(), synopsis,
                hasVersion ? properties.story().pageCount() : null, story.getCreatedAt(), story.getConfirmedAt(),
                readingSessionRepository.countByStoryIdAndCompletedAtIsNotNull(story.getId()), lastReadAt,
                story.getStatus() == StoryStatus.COMPLETED);
    }

    private HomeResponse.Counts counts(Long userId) {
        long creating = 0;
        long reviewing = 0;
        long unread = 0;
        long read = 0;
        long failed = 0;
        for (Object[] row : storyRepository.countByStatus(userId)) {
            StoryStatus status = (StoryStatus) row[0];
            long total = ((Number) row[1]).longValue();
            long unreadCount = row[2] == null ? 0 : ((Number) row[2]).longValue();
            if (LibraryFilter.CREATING.getStatuses().contains(status)) {
                creating += total;
            } else if (status == StoryStatus.REVIEWING) {
                reviewing += total;
            } else if (status == StoryStatus.COMPLETED) {
                unread += unreadCount;
                read += total - unreadCount;
            } else if (status == StoryStatus.FAILED) {
                failed += total;
            }
        }
        return new HomeResponse.Counts(creating, reviewing, unread, read, failed);
    }
}
