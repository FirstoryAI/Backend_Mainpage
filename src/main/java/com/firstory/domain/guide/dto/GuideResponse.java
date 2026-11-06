package com.firstory.domain.guide.dto;

import com.firstory.domain.guide.entity.GuidePrompt;
import com.firstory.domain.guide.entity.ReadingGuide;

import java.util.List;

/**
 * 대화 가이드 (GDE-01). 지금 보여 주는(확정된) 버전의 가이드. 가이드가 없는 버전이면 빈 값.
 *
 * @param prompts 읽는 중간에 나오는 대화 질문. seq 순서
 */
public record GuideResponse(Long storyId, String title, BeforeReading beforeReading, List<Prompt> prompts,
                            AfterReading afterReading) {

    public record BeforeReading(String intent, List<String> tips) {
    }

    /**
     * @param afterPage  이 쪽을 읽은 뒤에 보여준다
     * @param parentHint 아이가 대답할 때 부모가 받아 주는 법. 없을 수 있다
     * @param sceneNote  이 장면에서 일어난 일과 이 질문으로 아이가 해 볼 수 있는 것. 옛 동화는 없다
     */
    public record Prompt(Long id, int seq, int afterPage, String question, String purpose, String parentHint,
                         String sceneNote) {

        public static Prompt of(GuidePrompt prompt) {
            return new Prompt(prompt.getId(), prompt.getSeq(), prompt.getAfterPage(), prompt.getQuestion(),
                    prompt.getPurpose(), prompt.getParentHint(), prompt.getSceneNote());
        }
    }

    /**
     * @param ifUndecided 없을 수 있다
     */
    public record AfterReading(String bridgeQuestion, String ifUndecided) {
    }

    public static GuideResponse of(Long storyId, String title, ReadingGuide guide, List<GuidePrompt> prompts) {
        if (guide == null) {
            return new GuideResponse(storyId, title, new BeforeReading(null, List.of()), List.of(),
                    new AfterReading(null, null));
        }
        return new GuideResponse(storyId, title,
                new BeforeReading(guide.getBeforeIntent(), List.copyOf(guide.getBeforeTips())),
                prompts.stream().map(Prompt::of).toList(),
                new AfterReading(guide.getBridgeQuestion(), guide.getIfUndecided()));
    }
}
