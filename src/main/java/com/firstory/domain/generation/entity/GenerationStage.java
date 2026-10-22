package com.firstory.domain.generation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * 생성 진행 화면(GEN-01)의 단계. AI 그래프 노드 이름으로 정한다 (스펙 v0.3 7-5).
 */
@Getter
@RequiredArgsConstructor
public enum GenerationStage {

    DESIGN(1, "이야기 짜기", Set.of("design_story")),
    WRITE(2, "글 쓰기", Set.of("write_story", "polish_story")),
    ILLUSTRATE(3, "그림 그리기", Set.of("reading_guide", "illustrate", "illustrate_only")),
    RENDER(4, "책으로 묶기", Set.of("render"));

    private final int number;
    private final String label;
    private final Set<String> nodes;

    /**
     * @return 모르는 노드(또는 null)면 empty. 호출하는 쪽은 지금 단계를 그대로 둔다
     */
    public static Optional<GenerationStage> ofNode(String node) {
        if (node == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(stage -> stage.nodes.contains(node)).findFirst();
    }

    /**
     * 모르는 노드면 지금 단계(current)를 그대로 돌려준다.
     */
    public static GenerationStage ofNode(String node, GenerationStage current) {
        return ofNode(node).orElse(current);
    }

    /**
     * 1~2단계는 글을 만드는 중, 3~4단계는 그림을 만드는 중.
     */
    public boolean isText() {
        return number <= WRITE.number;
    }
}
