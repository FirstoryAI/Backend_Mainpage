package com.firstory.domain.generation.service;

import com.firstory.domain.generation.entity.ImageStatus;
import com.firstory.domain.generation.entity.StoryFiles;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.generation.repository.StoryPageRepository;
import com.firstory.domain.generation.repository.StoryVersionRepository;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.service.StoryLoader;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.storage.FileStorage;
import com.firstory.global.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그림 내려주기. 보안 규칙:
 * <ul>
 *   <li>내 동화(findByIdAndUserId) → 그 동화의 버전 → 그 버전의 쪽 순서로 확인한다. 하나라도 아니면 404 (403 아님)</li>
 *   <li>파일은 DB에 저장된 key로만 찾는다. 요청 값으로 경로를 만들지 않는다</li>
 *   <li>key가 stories/{storyId}/로 시작하는지 한 번 더 확인한다</li>
 * </ul>
 * 저장소를 S3로 바꿔도 이 서비스와 API 주소는 그대로 두고 FileStorage 구현만 바꾼다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoryImageService {

    private final StoryLoader loader;
    private final StoryVersionRepository versionRepository;
    private final StoryPageRepository pageRepository;
    private final FileStorage fileStorage;

    @Transactional(readOnly = true)
    public StoredFile pageImage(Long userId, Long storyId, Long versionId, int pageNo) {
        Story story = loader.findOwned(userId, storyId);
        return pageImage(story, findVersion(story, versionId), pageNo);
    }

    /**
     * 이미 확인한 동화와 그 동화의 버전으로 쪽 그림을 읽는다 (샘플 동화). 트랜잭션 안에서 부른다.
     */
    public StoredFile pageImage(Story story, StoryVersion version, int pageNo) {
        String key = pageRepository.findByVersionIdAndPageNo(version.getId(), pageNo)
                .filter(page -> page.getImageStatus() == ImageStatus.DONE)
                .map(page -> page.getImageKey())
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        return read(story, key);
    }

    /**
     * 표지로 보여 줄 그림 (StoryImageUrls.coverOrSheet와 같은 순서). 표지가 없으면 캐릭터 시트, 둘 다 없으면 IMAGE_NOT_FOUND.
     * 이미 확인한 동화와 그 동화의 버전으로 읽는다 (샘플 동화). 트랜잭션 안에서 부른다.
     */
    public StoredFile coverOrSheet(Story story, StoryVersion version) {
        return read(story, version.getCoverKey() != null ? version.getCoverKey() : version.getCharacterSheetKey());
    }

    @Transactional(readOnly = true)
    public StoredFile characterSheet(Long userId, Long storyId, Long versionId) {
        Story story = loader.findOwned(userId, storyId);
        StoryVersion version = findVersion(story, versionId);
        return read(story, version.getCharacterSheetKey());
    }

    @Transactional(readOnly = true)
    public StoredFile cover(Long userId, Long storyId, Long versionId) {
        Story story = loader.findOwned(userId, storyId);
        StoryVersion version = findVersion(story, versionId);
        return read(story, version.getCoverKey());
    }

    private StoryVersion findVersion(Story story, Long versionId) {
        return versionRepository.findByIdAndStoryId(versionId, story.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSION_NOT_FOUND));
    }

    private StoredFile read(Story story, String key) {
        if (key == null) {
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND);
        }
        if (!StoryFiles.belongsTo(key, story.getId())) {
            // 결과 저장 때 이미 막지만 한 번 더 확인한다. key는 남기지 않는다
            log.warn("다른 동화의 경로를 가리키는 그림이라 내려주지 않아요: story_id={}", story.getId());
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND);
        }
        try {
            return fileStorage.read(key).orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        } catch (IllegalArgumentException e) {
            log.warn("안전하지 않은 그림 key예요: story_id={}", story.getId());
            throw new BusinessException(ErrorCode.IMAGE_NOT_FOUND);
        }
    }
}
