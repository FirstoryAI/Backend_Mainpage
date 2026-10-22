package com.firstory.domain.generation.dto;

/**
 * 그림 API 주소. DB id로만 만든다. 파일 key나 서버 경로를 응답에 넣지 않는다.
 * S3로 바꿔도 이 주소는 그대로 둔다.
 */
public final class StoryImageUrls {

    private StoryImageUrls() {
    }

    public static String page(Long storyId, Long versionId, int pageNo) {
        return "/api/stories/" + storyId + "/versions/" + versionId + "/pages/" + pageNo + "/image";
    }

    public static String characterSheet(Long storyId, Long versionId) {
        return "/api/stories/" + storyId + "/versions/" + versionId + "/character-sheet";
    }
}
