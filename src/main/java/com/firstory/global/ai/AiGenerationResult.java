package com.firstory.global.ai;

import com.firstory.domain.generation.entity.TextPosition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 부모 검토(REVIEWING)에서 멈췄을 때의 결과 (스펙 v0.3 7-6).
 * 파일 경로(imagePath, path)는 FileStorage key다. 저장하기 전에 이 동화의 경로인지 확인한다.
 *
 * @param safety 완성 본문의 위기 신호. 없으면 OK로 본다
 */
public record AiGenerationResult(int runNo, String checkpointId, String title, Map<String, Object> design,
                                 List<CharacterSheet> characterSheets, List<Page> pages, Guide guide, Cost cost,
                                 SafetyResult safety) {

    public AiGenerationResult {
        design = design == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(design));
        characterSheets = characterSheets == null ? List.of() : List.copyOf(characterSheets);
        pages = pages == null ? List.of() : List.copyOf(pages);
        cost = cost == null ? new Cost(0, 0, 0) : cost;
        safety = safety == null ? SafetyResult.ok() : safety;
    }

    public record CharacterSheet(String role, String name, String path) {
    }

    /**
     * @param textPosition 그림 위 글 자리 (AI text_position. 없으면 BOTTOM)
     * @param imageStatus  done | failed (AI 값 그대로)
     */
    public record Page(int pageNo, String text, String imagePrompt, TextPosition textPosition, String imagePath,
                       String imageStatus) {

        public boolean imageDone() {
            return "done".equalsIgnoreCase(imageStatus) && imagePath != null;
        }
    }

    /**
     * 04-guide.json. synopsis(동화 줄거리, 없으면 null) / before_reading → beforeIntent, beforeTips /
     * after_reading → bridgeQuestion, ifUndecided.
     */
    public record Guide(String synopsis, String beforeIntent, List<String> beforeTips, String bridgeQuestion,
                        String ifUndecided, List<Prompt> prompts) {

        public Guide {
            beforeTips = beforeTips == null ? List.of() : List.copyOf(beforeTips);
            prompts = prompts == null ? List.of() : List.copyOf(prompts);
        }
    }

    /**
     * @param text 질문 본문. DB에서는 guide_prompts.question
     */
    public record Prompt(int afterPage, String text, String purpose, String parentHint) {

        public Prompt {
            Objects.requireNonNull(text);
        }
    }

    public record Cost(int inputTokens, int outputTokens, int imageCount) {
    }

    /**
     * 결과에 들어 있는 모든 파일 key (캐릭터 시트, 쪽 그림).
     */
    public List<String> fileKeys() {
        return Stream.concat(characterSheets.stream().map(CharacterSheet::path), pages.stream().map(Page::imagePath))
                .filter(Objects::nonNull)
                .toList();
    }

    // 본문과 가이드에 부모 입력이 반영되어 있어 로그에 남기지 않는다
    @Override
    public String toString() {
        return "AiGenerationResult[runNo=" + runNo + ", pages=" + pages.size() + "]";
    }
}
