package com.firstory.domain.story.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.firstory.domain.story.entity.StoryStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class StoryStatusTest {

    // TASK-01 표 그대로
    private static final Map<StoryStatus, Set<StoryStatus>> ALLOWED = new EnumMap<>(StoryStatus.class);

    static {
        ALLOWED.put(DRAFT, EnumSet.of(INTERVIEWING, BLOCKED));
        ALLOWED.put(INTERVIEWING, EnumSet.of(CONFIRMING, BLOCKED));
        ALLOWED.put(CONFIRMING, EnumSet.of(REQUESTED, BLOCKED));
        ALLOWED.put(REQUESTED, EnumSet.of(GENERATING_TEXT, GENERATING_IMAGES, FAILED));
        ALLOWED.put(GENERATING_TEXT, EnumSet.of(GENERATING_IMAGES, FAILED));
        ALLOWED.put(GENERATING_IMAGES, EnumSet.of(REVIEWING, FAILED));
        ALLOWED.put(REVIEWING, EnumSet.of(REQUESTED, COMPLETED));
        ALLOWED.put(FAILED, EnumSet.of(REQUESTED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(StoryStatus.class));
        ALLOWED.put(BLOCKED, EnumSet.noneOf(StoryStatus.class));
    }

    @ParameterizedTest
    @EnumSource(StoryStatus.class)
    void 표에_있는_전이만_허용한다(StoryStatus from) {
        for (StoryStatus to : StoryStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s → %s", from, to)
                    .isEqualTo(ALLOWED.get(from).contains(to));
        }
    }

    @Test
    void 대표적인_금지_전이() {
        assertThat(COMPLETED.canTransitionTo(REQUESTED)).isFalse();
        assertThat(DRAFT.canTransitionTo(REVIEWING)).isFalse();
        assertThat(BLOCKED.canTransitionTo(DRAFT)).isFalse();
    }

    @Test
    void 만드는_중과_생성_중_구분() {
        assertThat(EnumSet.allOf(StoryStatus.class).stream().filter(StoryStatus::isCreating))
                .containsExactlyInAnyOrder(DRAFT, INTERVIEWING, CONFIRMING);
        assertThat(EnumSet.allOf(StoryStatus.class).stream().filter(StoryStatus::isGenerating))
                .containsExactlyInAnyOrder(REQUESTED, GENERATING_TEXT, GENERATING_IMAGES);
    }
}
