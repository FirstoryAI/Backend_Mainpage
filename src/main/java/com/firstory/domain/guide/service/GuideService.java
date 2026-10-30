package com.firstory.domain.guide.service;

import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.guide.dto.GuideResponse;
import com.firstory.domain.guide.entity.GuidePrompt;
import com.firstory.domain.guide.entity.ReadingGuide;
import com.firstory.domain.guide.repository.GuidePromptRepository;
import com.firstory.domain.guide.repository.ReadingGuideRepository;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.service.StoryLoader;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 대화 가이드 (GDE-01). 부모 검토(REVIEWING)와 확정(COMPLETED)에서 지금 보여 주는 버전의 가이드를 보여준다.
 * 함께 읽기는 확정된 동화만 (completedStory).
 */
@Service
@RequiredArgsConstructor
public class GuideService {

    // 미리보기(REV-01)에서도 동화 설명과 가이드를 보여 주려고 REVIEWING도 연다
    private static final Set<StoryStatus> GUIDE_STATUSES = EnumSet.of(StoryStatus.REVIEWING, StoryStatus.COMPLETED);

    private final StoryLoader loader;
    private final ReadingGuideRepository guideRepository;
    private final GuidePromptRepository promptRepository;

    @Transactional(readOnly = true)
    public GuideResponse guide(Long userId, Long storyId) {
        Story story = loader.findOwned(userId, storyId);
        if (!GUIDE_STATUSES.contains(story.getStatus()) || story.getCurrentVersion() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        ReadingGuide guide = guideRepository.findByVersionId(story.getCurrentVersion().getId()).orElse(null);
        List<GuidePrompt> prompts = guide == null ? List.of() : promptRepository.findAllByGuideIdOrderBySeqAsc(guide.getId());
        return GuideResponse.of(story.getId(), story.getTitle(), guide, prompts);
    }

    /**
     * 내 동화이고 확정(COMPLETED)됐으며 버전이 있는지. 아니면 STORY_NOT_FOUND 또는 INVALID_STATUS_TRANSITION.
     */
    public static Story completedStory(StoryLoader loader, Long userId, Long storyId) {
        Story story = loader.findOwned(userId, storyId);
        StoryVersion version = story.getCurrentVersion();
        if (story.getStatus() != StoryStatus.COMPLETED || version == null) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        return story;
    }
}
