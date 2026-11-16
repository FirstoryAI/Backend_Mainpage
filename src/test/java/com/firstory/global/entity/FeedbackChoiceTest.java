package com.firstory.global.entity;

import com.firstory.domain.generation.entity.ReadingIntent;
import com.firstory.domain.generation.entity.SituationReflection;
import com.firstory.domain.generation.entity.StorySatisfaction;
import com.firstory.domain.reading.entity.ChildInterest;
import com.firstory.domain.reading.entity.ConversationFlow;
import com.firstory.domain.reading.entity.ConversationHelpfulness;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackChoiceTest {

    @Test
    void 코드와_화면_문구로_찾는다() {
        assertThat(SituationReflection.from("PARTLY")).isEqualTo(SituationReflection.PARTLY);
        assertThat(SituationReflection.from("어느 정도 반영되었어요")).isEqualTo(SituationReflection.PARTLY);
        assertThat(ConversationHelpfulness.from("별로 도움이 되지 않았어요")).isEqualTo(ConversationHelpfulness.NOT_MUCH);
    }

    @Test
    void 코드도_문구도_아니면_IllegalArgumentException() {
        assertThatThrownBy(() -> ReadingIntent.from("읽을래요")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReadingIntent.from("definitely")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 프론트_팝업의_선택지_문구와_같다() {
        // 프론트 FeedbackDialog.jsx의 previewQuestions, readingQuestions (11/16 기준)
        assertThat(labels(SituationReflection.values()))
                .containsExactly("잘 반영되었어요", "어느 정도 반영되었어요", "아쉬운 부분이 있어요");
        assertThat(labels(StorySatisfaction.values())).containsExactly("만족해요", "보통이에요", "아쉬워요");
        assertThat(labels(ReadingIntent.values()))
                .containsExactly("꼭 읽어보고 싶어요", "기회가 되면 읽어보고 싶어요", "아직 읽어볼 생각이 없어요");
        assertThat(labels(ChildInterest.values())).containsExactly("매우 흥미로워했어요", "보통이었어요", "흥미를 보이지 않았어요");
        assertThat(labels(ConversationFlow.values()))
                .containsExactly("자연스럽게 대화를 나눴어요", "평소와 비슷했어요", "대화로 이어지지 않았어요");
        assertThat(labels(ConversationHelpfulness.values()))
                .containsExactly("많은 도움이 되었어요", "조금 도움이 되었어요", "별로 도움이 되지 않았어요");
    }

    private static List<String> labels(FeedbackChoice[] choices) {
        return Arrays.stream(choices).map(FeedbackChoice::getLabel).toList();
    }
}
