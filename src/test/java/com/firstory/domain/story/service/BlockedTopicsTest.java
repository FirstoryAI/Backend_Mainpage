package com.firstory.domain.story.service;

import com.firstory.domain.story.entity.SituationCategory;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockedTopicsTest {

    @Test
    void SEPARATION만_막는다() {
        BlockedTopics topics = BlockedTopics.of(Set.of(SituationCategory.SEPARATION));

        assertThat(topics.blocks(SituationCategory.SEPARATION)).isTrue();
        assertThat(topics.blocks(SituationCategory.FEAR)).isFalse();
        assertThat(topics.blocksAi("separation")).isTrue();
        assertThat(topics.blocksAi("fear")).isFalse();
        assertThat(topics.blocksAi(null)).isFalse();
        assertThat(topics.aiValues()).containsExactly("separation");
        assertThatThrownBy(() -> topics.requireOpen(SituationCategory.SEPARATION))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_UNAVAILABLE);
        topics.requireOpen(SituationCategory.FRIEND_CONFLICT);
        topics.requireOpen(null);
    }

    @Test
    void 비우면_아무것도_막지_않는다() {
        BlockedTopics topics = BlockedTopics.of(Set.of());

        assertThat(topics.blocks(SituationCategory.SEPARATION)).isFalse();
        assertThat(topics.blocksAi("separation")).isFalse();
        assertThat(topics.aiValues()).isEmpty();
        topics.requireOpen(SituationCategory.SEPARATION);
    }
}
