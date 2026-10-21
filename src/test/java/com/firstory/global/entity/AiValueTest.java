package com.firstory.global.entity;

import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.InterestCode;
import com.firstory.domain.child.entity.Trait;
import com.firstory.domain.story.entity.Emotion;
import com.firstory.domain.story.entity.MessageDirection;
import com.firstory.domain.story.entity.MomentReaction;
import com.firstory.domain.story.entity.SituationCategory;
import com.firstory.domain.story.entity.SituationTiming;
import com.firstory.domain.story.entity.Tone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class AiValueTest {

    // AI로 보내는 enum 전부. 새 enum이 AiValue를 구현하면 여기에도 넣는다
    static final List<Class<? extends AiValue>> AI_ENUMS = List.of(Gender.class, Trait.class, InterestCode.class,
            SituationCategory.class, SituationTiming.class, MomentReaction.class, Emotion.class,
            MessageDirection.class, Tone.class);

    @Test
    void 모든_값의_aiValue는_소문자_이름이다() {
        for (Class<? extends AiValue> type : AI_ENUMS) {
            assertThat(type.isEnum()).as(type.getSimpleName()).isTrue();
            for (AiValue value : type.getEnumConstants()) {
                assertThat(value.aiValue())
                        .as(type.getSimpleName() + "." + value.name())
                        .isEqualTo(value.name().toLowerCase(Locale.ROOT))
                        .matches("[a-z_]+");
            }
        }
    }

    @Test
    void AI와_맞춘_값_이름() {
        assertThat(Gender.UNSPECIFIED.aiValue()).isEqualTo("unspecified");
        assertThat(Trait.CAUTIOUS.aiValue()).isEqualTo("cautious");
        assertThat(SituationCategory.FRIEND_CONFLICT.aiValue()).isEqualTo("friend_conflict");
        assertThat(MomentReaction.ANGRY_OUTBURST.aiValue()).isEqualTo("angry_outburst");
        assertThat(MessageDirection.PERSPECTIVE_TAKING.aiValue()).isEqualTo("perspective_taking");
    }
}
