package com.firstory.domain.story.service;

import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryInput;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.repository.StoryInputRepository;
import com.firstory.domain.story.repository.StoryRepository;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 동화 만들기 서비스가 같이 쓰는 조회와 상태 검사. 트랜잭션 안에서 부른다.
 * 다른 회원의 동화와 지운 동화는 STORY_NOT_FOUND(404)다.
 */
@Component
@RequiredArgsConstructor
public class StoryLoader {

    private final StoryRepository storyRepository;
    private final StoryInputRepository storyInputRepository;

    public Story findOwned(Long userId, Long storyId) {
        return storyRepository.findByIdAndUserId(storyId, userId).orElseThrow(StoryLoader::notFound);
    }

    /**
     * 변경 요청용. 차단된 동화는 STORY_BLOCKED, 허용하지 않는 상태는 INVALID_STATUS_TRANSITION.
     */
    public Story findEditable(Long userId, Long storyId, Set<StoryStatus> allowed) {
        return checkEditable(findOwned(userId, storyId), allowed);
    }

    /**
     * findEditable과 같고 동화 행을 잠근다. 트랜잭션에서 이 동화를 처음 읽을 때 불러야 지금 상태를 본다
     * (이미 읽은 엔티티는 잠가도 다시 읽지 않는다).
     */
    public Story findEditableForUpdate(Long userId, Long storyId, Set<StoryStatus> allowed) {
        Story story = storyRepository.findOwnedForUpdate(storyId, userId).orElseThrow(StoryLoader::notFound);
        return checkEditable(story, allowed);
    }

    private static Story checkEditable(Story story, Set<StoryStatus> allowed) {
        if (story.getStatus() == StoryStatus.BLOCKED) {
            throw new BusinessException(ErrorCode.STORY_BLOCKED);
        }
        if (!allowed.contains(story.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        return story;
    }

    /**
     * AI 호출 뒤 저장할 때 다시 읽고 잠근다. AI를 부르는 사이에 상태가 바뀌었으면 INVALID_STATUS_TRANSITION.
     *
     * @param loaded AI 호출 전에 조회한 동화
     */
    public Story lockUnchanged(Story loaded) {
        Story story = storyRepository.findByIdForUpdate(loaded.getId()).orElseThrow(StoryLoader::notFound);
        if (story.getStatus() != loaded.getStatus()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        return story;
    }

    public StoryInput input(Story story) {
        return storyInputRepository.findById(story.getId()).orElseThrow(StoryLoader::notFound);
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.STORY_NOT_FOUND);
    }
}
