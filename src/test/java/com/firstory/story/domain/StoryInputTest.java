package com.firstory.story.domain;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static com.firstory.support.Fixtures.story;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryInputTest {

    @Test
    void 앞으로_겪을_일에는_감정을_넣을_수_없다() {
        StoryInput input = StoryInput.begin(story(user()));

        assertThatThrownBy(() -> input.writeSituation(SituationTiming.UPCOMING, "HOSPITAL", "설명", null, "SAD"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMOTION_ONLY_FOR_PAST);
    }

    @Test
    void 있었던_일에는_감정을_넣을_수_있다() {
        StoryInput input = StoryInput.begin(story(user()));

        input.writeSituation(SituationTiming.PAST, "PET_LOSS", "설명", "울었어요", "SAD");

        assertThat(input.getEmotion()).isEqualTo("SAD");
    }
}
