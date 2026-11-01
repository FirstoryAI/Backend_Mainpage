package com.firstory.domain.story.entity;

import org.junit.jupiter.api.Test;

import static com.firstory.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewTurnTest {

    @Test
    void 인터뷰_차례는_1에서_15() {
        Story story = story(user());

        assertThat(InterviewTurn.MAX_TURNS).isEqualTo(15);
        assertThat(InterviewTurn.ask(story, 1, "질문").getTurnNo()).isEqualTo(1);
        assertThat(InterviewTurn.ask(story, 15, "질문").getTurnNo()).isEqualTo(15);
        assertThatThrownBy(() -> InterviewTurn.ask(story, 0, "질문")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterviewTurn.ask(story, 16, "질문")).isInstanceOf(IllegalArgumentException.class);
    }
}
