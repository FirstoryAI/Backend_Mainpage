package com.firstory.domain.story.entity;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.firstory.support.Fixtures.story;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryInputTest {

    StoryInput input;

    @BeforeEach
    void begin() {
        input = StoryInput.begin(story(user()));
    }

    @Test
    void 상황_설명은_60자까지() {
        input.writeSituation(SituationTiming.PAST, SituationCategory.FEAR, null, "가".repeat(60), null, null, null);

        assertThat(input.getDescription()).hasSize(60);
    }

    @Test
    void 상황_설명이_61자면_예외() {
        assertError(() -> input.writeSituation(SituationTiming.PAST, SituationCategory.FEAR, null, "가".repeat(61),
                null, null, null), ErrorCode.DESCRIPTION_TOO_LONG);
    }

    @Test
    void 상황_분류가_OTHER면_직접_입력이_필요하다() {
        assertError(() -> input.writeSituation(SituationTiming.PAST, SituationCategory.OTHER, " ", "설명",
                null, null, null), ErrorCode.CUSTOM_VALUE_REQUIRED);

        input.writeSituation(SituationTiming.PAST, SituationCategory.OTHER, " 동생이 생겼어요 ", "설명", null, null, null);

        assertThat(input.getSituationCustomCategory()).isEqualTo("동생이 생겼어요");
    }

    @Test
    void 상황_분류_직접_입력은_30자까지() {
        assertError(() -> input.writeSituation(SituationTiming.PAST, SituationCategory.OTHER, "가".repeat(31), "설명",
                null, null, null), ErrorCode.CUSTOM_VALUE_TOO_LONG);
    }

    @Test
    void 반응이_OTHER면_직접_입력이_필요하다() {
        assertError(() -> input.writeSituation(SituationTiming.PAST, SituationCategory.FEAR, null, "설명",
                List.of(MomentReaction.OTHER), null, null), ErrorCode.CUSTOM_VALUE_REQUIRED);
        assertError(() -> input.writeSituation(SituationTiming.PAST, SituationCategory.FEAR, null, "설명",
                List.of(MomentReaction.OTHER), "가".repeat(31), null), ErrorCode.CUSTOM_VALUE_TOO_LONG);
    }

    @Test
    void OTHER가_아니면_직접_입력은_버린다() {
        input.writeSituation(SituationTiming.PAST, SituationCategory.FEAR, "버려져요", "설명",
                List.of(MomentReaction.CRIED), "이것도 버려져요", null);

        assertThat(input.getSituationCustomCategory()).isNull();
        assertThat(input.getMomentCustomReaction()).isNull();
    }

    @Test
    void 앞으로_겪을_일에는_감정을_고를_수_없다() {
        assertError(() -> input.writeSituation(SituationTiming.UPCOMING, SituationCategory.NEW_ENVIRONMENT, null,
                "설명", null, null, List.of(Emotion.SCARED)), ErrorCode.MOMENT_ONLY_FOR_PAST);
    }

    @Test
    void 앞으로_겪을_일에는_반응을_고를_수_없다() {
        assertError(() -> input.writeSituation(SituationTiming.UPCOMING, SituationCategory.NEW_ENVIRONMENT, null,
                "설명", List.of(MomentReaction.CRIED), null, List.of()), ErrorCode.MOMENT_ONLY_FOR_PAST);
    }

    @Test
    void 앞으로_겪을_일은_반응과_감정이_비어_있다() {
        input.writeSituation(SituationTiming.UPCOMING, SituationCategory.NEW_ENVIRONMENT, null, "설명",
                List.of(), "버려져요", null);

        assertThat(input.getMomentReactions()).isEmpty();
        assertThat(input.getMomentCustomReaction()).isNull();
        assertThat(input.getEmotions()).isEmpty();
    }

    @Test
    void 반응과_감정은_중복을_지우고_선언_순서로_정렬한다() {
        input.writeSituation(SituationTiming.PAST, SituationCategory.FRIEND_CONFLICT, null, " 설명 ",
                Arrays.asList(MomentReaction.OTHER, MomentReaction.CRIED, null, MomentReaction.CRIED), "방에 숨었어요",
                List.of(Emotion.SCARED, Emotion.SAD, Emotion.SCARED));

        assertThat(input.getDescription()).isEqualTo("설명");
        assertThat(input.getMomentReactions()).containsExactly(MomentReaction.CRIED, MomentReaction.OTHER);
        assertThat(input.getMomentCustomReaction()).isEqualTo("방에 숨었어요");
        assertThat(input.getEmotions()).containsExactly(Emotion.SAD, Emotion.SCARED);
    }

    @Test
    void 실패하면_이전_값이_그대로_남는다() {
        input.writeSituation(SituationTiming.PAST, SituationCategory.FEAR, null, "처음", null, null, null);

        assertError(() -> input.writeSituation(SituationTiming.PAST, SituationCategory.OTHER, null, "다시",
                null, null, null), ErrorCode.CUSTOM_VALUE_REQUIRED);

        assertThat(input.getSituationCategory()).isEqualTo(SituationCategory.FEAR);
        assertThat(input.getDescription()).isEqualTo("처음");
    }

    @Test
    void 인터뷰를_마치면_확정된_context를_저장한다() {
        input.finishInterview("요약", InterviewEndReason.SUFFICIENT, Map.of("parent", Map.of("concern", "걱정")));

        assertThat(input.getInterviewEndReason()).isEqualTo(InterviewEndReason.SUFFICIENT);
        assertThat(input.getFinalContext()).containsKey("parent");
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(code);
    }
}
