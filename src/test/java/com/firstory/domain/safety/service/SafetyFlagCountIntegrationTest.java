package com.firstory.domain.safety.service;

import com.firstory.domain.safety.entity.SafetyAction;
import com.firstory.domain.safety.entity.SafetyFlag;
import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.safety.repository.SafetyFlagRepository;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.repository.StoryRepository;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static com.firstory.support.Fixtures.NOW;
import static com.firstory.support.Fixtures.story;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설정값(firstory.policy.safety-revise-limit=2)으로 동작하는 SafetyPolicy와 DB의 단계별 횟수를 함께 확인한다.
 */
class SafetyFlagCountIntegrationTest extends IntegrationTest {

    @Autowired
    SafetyPolicy safetyPolicy;

    @Autowired
    SafetyFlagRepository safetyFlagRepository;

    @Autowired
    StoryRepository storyRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TransactionTemplate tx;

    Story story;

    @BeforeEach
    void createStory() {
        story = tx.execute(status -> storyRepository.save(story(userRepository.save(user()))));
    }

    @Test
    void 같은_동화에_REVISE가_1번_있으면_다음_REVISE는_차단() {
        assertThat(detect(SafetyLevel.REVISE)).isEqualTo(SafetyAction.ASK_REVISE);

        assertThat(detect(SafetyLevel.REVISE)).isEqualTo(SafetyAction.BLOCK);
    }

    @Test
    void CRISIS는_REVISE_횟수와_따로_센다() {
        assertThat(detect(SafetyLevel.CRISIS)).isEqualTo(SafetyAction.BLOCK);

        // CRISIS가 있어도 REVISE는 1번째다. 순번도 단계별이라 둘 다 1번
        assertThat(detect(SafetyLevel.REVISE)).isEqualTo(SafetyAction.ASK_REVISE);
        assertThat(safetyFlagRepository.countByStoryIdAndLevel(story.getId(), SafetyLevel.REVISE)).isOne();
        assertThat(safetyFlagRepository.countByStoryIdAndLevel(story.getId(), SafetyLevel.CRISIS)).isOne();
    }

    @Test
    void 다른_동화의_REVISE는_세지_않는다() {
        detect(SafetyLevel.REVISE);
        story = tx.execute(status -> storyRepository.save(story(userRepository.save(user()))));

        assertThat(detect(SafetyLevel.REVISE)).isEqualTo(SafetyAction.ASK_REVISE);
    }

    // TASK-05의 흐름을 흉내 낸다: 이번 것을 기록하고, REVISE 횟수(이번 포함)로 판단한다
    private SafetyAction detect(SafetyLevel level) {
        return tx.execute(status -> {
            int occurrenceNo = (int) safetyFlagRepository.countByStoryIdAndLevel(story.getId(), level) + 1;
            safetyFlagRepository.save(SafetyFlag.record(storyRepository.getReferenceById(story.getId()),
                    SafetySource.FORM, level, null, "test", null, occurrenceNo, NOW));
            int reviseCount = (int) safetyFlagRepository.countByStoryIdAndLevel(story.getId(), SafetyLevel.REVISE);
            return safetyPolicy.decide(level, reviseCount);
        });
    }
}
