package com.firstory.support;

import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.generation.repository.GenerationRunRepository;
import com.firstory.domain.generation.repository.StoryVersionRepository;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryInput;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.repository.StoryInputRepository;
import com.firstory.domain.story.repository.StoryRepository;
import com.firstory.domain.user.repository.UserRepository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 서재 테스트용. API를 거치지 않고 원하는 상태의 동화를 DB에 바로 만든다 (상태 전이 규칙은 그대로 따른다).
 * REVIEWING 이후 상태는 실행과 버전(캐릭터 시트 key 포함)을 함께 만든다.
 */
public class StoryStates {

    private final TransactionTemplate tx;
    private final UserRepository userRepository;
    private final StoryRepository storyRepository;
    private final StoryInputRepository storyInputRepository;
    private final GenerationRunRepository runRepository;
    private final StoryVersionRepository versionRepository;

    public StoryStates(TransactionTemplate tx, UserRepository userRepository, StoryRepository storyRepository,
                       StoryInputRepository storyInputRepository, GenerationRunRepository runRepository,
                       StoryVersionRepository versionRepository) {
        this.tx = tx;
        this.userRepository = userRepository;
        this.storyRepository = storyRepository;
        this.storyInputRepository = storyInputRepository;
        this.runRepository = runRepository;
        this.versionRepository = versionRepository;
    }

    /**
     * @param read COMPLETED일 때 함께 읽은 적이 있는지
     */
    public long create(Long userId, StoryStatus target, boolean read, Instant now) {
        return tx.execute(status -> {
            Story story = storyRepository.save(Story.start(userRepository.getReferenceById(userId), null,
                    UUID.randomUUID().toString()));
            storyInputRepository.save(StoryInput.begin(story));
            if (target == StoryStatus.BLOCKED) {
                story.block();
                return story.getId();
            }
            for (StoryStatus next : List.of(StoryStatus.INTERVIEWING, StoryStatus.CONFIRMING, StoryStatus.REQUESTED,
                    StoryStatus.GENERATING_TEXT, StoryStatus.GENERATING_IMAGES)) {
                if (story.getStatus() == target) {
                    return story.getId();
                }
                story.changeStatus(next);
            }
            if (story.getStatus() == target) {
                return story.getId();
            }
            if (target == StoryStatus.FAILED) {
                story.changeStatus(StoryStatus.FAILED);
                return story.getId();
            }
            GenerationRun run = runRepository.save(Fixtures.run(story, 1));
            StoryVersion version = versionRepository.save(StoryVersion.create(story, run, 1, null,
                    "동화 " + story.getId(), "cp-1", null, "stories/" + story.getId() + "/runs/1/images/sheet-main-1.png",
                    null));
            story.changeStatus(StoryStatus.REVIEWING);
            story.showVersion(version);
            if (target == StoryStatus.COMPLETED) {
                story.confirm(now);
                if (read) {
                    story.markFirstReadCompleted(now);
                }
            }
            return story.getId();
        });
    }
}
