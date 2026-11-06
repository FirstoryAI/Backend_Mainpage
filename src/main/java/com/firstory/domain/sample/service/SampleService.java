package com.firstory.domain.sample.service;

import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.generation.repository.StoryPageRepository;
import com.firstory.domain.generation.service.StoryImageService;
import com.firstory.domain.sample.dto.SampleBookResponse;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.repository.StoryRepository;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.storage.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 샘플 카드의 미리 만들어 둔 동화 (로그인 없이). 확정된(COMPLETED) 동화의 지금 버전만 보여 준다.
 * 연결이 없거나, 동화를 지웠거나, 아직 확정 전이면 모두 SAMPLE_NOT_FOUND다.
 */
@Slf4j
@Service
public class SampleService {

    private final SampleCatalog catalog;
    private final StoryRepository storyRepository;
    private final StoryPageRepository pageRepository;
    private final StoryImageService imageService;
    private final int pageCount;

    public SampleService(SampleCatalog catalog, StoryRepository storyRepository, StoryPageRepository pageRepository,
                         StoryImageService imageService, FirstoryProperties properties) {
        this.catalog = catalog;
        this.storyRepository = storyRepository;
        this.pageRepository = pageRepository;
        this.imageService = imageService;
        this.pageCount = properties.story().pageCount();
    }

    @Transactional(readOnly = true)
    public SampleBookResponse book(String cardId) {
        Story story = sampleStory(cardId);
        StoryVersion version = story.getCurrentVersion();
        return SampleBookResponse.of(cardId, story.getTitle(), version, pageCount,
                pageRepository.findAllByVersionIdOrderByPageNoAsc(version.getId()));
    }

    @Transactional(readOnly = true)
    public StoredFile cover(String cardId) {
        Story story = sampleStory(cardId);
        return imageService.coverOrSheet(story, story.getCurrentVersion());
    }

    @Transactional(readOnly = true)
    public StoredFile pageImage(String cardId, int pageNo) {
        Story story = sampleStory(cardId);
        return imageService.pageImage(story, story.getCurrentVersion(), pageNo);
    }

    private Story sampleStory(String cardId) {
        Long storyId = catalog.storyId(cardId).orElseThrow(() -> new BusinessException(ErrorCode.SAMPLE_NOT_FOUND));
        Story story = storyRepository.findById(storyId).orElse(null);
        if (story == null || story.getStatus() != StoryStatus.COMPLETED || story.getCurrentVersion() == null) {
            // 연결은 했는데 보여 줄 수 없는 동화다. 설정(SAMPLE_STORIES)을 고쳐야 한다
            log.warn("샘플 카드에 연결한 동화를 보여 줄 수 없어요: card={}, story_id={}", cardId, storyId);
            throw new BusinessException(ErrorCode.SAMPLE_NOT_FOUND);
        }
        return story;
    }
}
