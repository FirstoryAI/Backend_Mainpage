package com.firstory.domain.generation.entity;

/**
 * 동화 결과물 파일 key 규칙. 모든 파일은 `stories/{storyId}/` 아래에 있어야 한다.
 *
 * <pre>
 * stories/{storyId}/runs/{runNo}/images/page-01.png
 * stories/{storyId}/runs/{runNo}/images/sheet-{role}-{n}.png
 * </pre>
 * 그림만 다시 그린 버전은 바뀌지 않은 쪽이 이전 실행의 key를 그대로 쓴다.
 */
public final class StoryFiles {

    private StoryFiles() {
    }

    public static String storyPrefix(Long storyId) {
        return "stories/" + storyId + "/";
    }

    /**
     * AI에 넘기는 output_prefix.
     */
    public static String runPrefix(Long storyId, int runNo) {
        return storyPrefix(storyId) + "runs/" + runNo + "/";
    }

    /**
     * 다른 동화의 파일을 가리키지 않는지. 상위 폴더(..)가 들어 있으면 이 동화 것이 아니다.
     */
    public static boolean belongsTo(String key, Long storyId) {
        return key != null && key.startsWith(storyPrefix(storyId)) && !key.contains("..") && !key.contains("\\");
    }
}
