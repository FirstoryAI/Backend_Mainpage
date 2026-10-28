package com.firstory.domain.generation.dto;

import com.firstory.domain.generation.entity.StoryVersion;

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

    public static String cover(Long storyId, Long versionId) {
        return "/api/stories/" + storyId + "/versions/" + versionId + "/cover";
    }

    /**
     * 표지로 보여 줄 그림. 표지가 있으면 표지, 없으면(이전 버전, 표지 실패) 캐릭터 시트, 둘 다 없으면 null.
     */
    public static String coverOrSheet(Long storyId, StoryVersion version) {
        if (version.getCoverKey() != null) {
            return cover(storyId, version.getId());
        }
        return version.getCharacterSheetKey() == null ? null : characterSheet(storyId, version.getId());
    }
}
