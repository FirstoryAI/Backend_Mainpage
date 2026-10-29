package com.firstory.domain.generation.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationStageTest {

    @ParameterizedTest
    @CsvSource({
            "design_story, 1, 이야기 짜기",
            "write_story, 2, 글 쓰기",
            "polish_story, 2, 글 쓰기",
            "reading_guide, 3, 그림 그리기",
            "safety_output, 3, 그림 그리기",
            "illustrate, 3, 그림 그리기",
            "illustrate_only, 3, 그림 그리기",
            "render, 4, 책으로 묶기"
    })
    void 노드_이름으로_단계를_정한다(String node, int number, String label) {
        GenerationStage stage = GenerationStage.ofNode(node).orElseThrow();

        assertThat(stage.getNumber()).isEqualTo(number);
        assertThat(stage.getLabel()).isEqualTo(label);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"parent_review", "unknown_node"})
    void 모르는_노드는_지금_단계를_유지한다(String node) {
        assertThat(GenerationStage.ofNode(node)).isEmpty();
        assertThat(GenerationStage.ofNode(node, GenerationStage.WRITE)).isEqualTo(GenerationStage.WRITE);
    }

    @ParameterizedTest
    @CsvSource({"DESIGN, true", "WRITE, true", "ILLUSTRATE, false", "RENDER, false"})
    void 단계_1_2는_글_3_4는_그림(GenerationStage stage, boolean text) {
        assertThat(stage.isText()).isEqualTo(text);
    }
}
