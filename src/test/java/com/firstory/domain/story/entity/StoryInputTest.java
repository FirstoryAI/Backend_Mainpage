package com.firstory.domain.story.entity;

import org.junit.jupiter.api.Test;

import static com.firstory.support.Fixtures.story;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

class StoryInputTest {

    @Test
    void 상황을_적는다() {
        StoryInput input = StoryInput.begin(story(user()));

        input.writeSituation(SituationTiming.PAST, "FRIEND_CONFLICT", "설명");

        assertThat(input.getSituationTiming()).isEqualTo(SituationTiming.PAST);
        assertThat(input.getDescription()).isEqualTo("설명");
    }
}
