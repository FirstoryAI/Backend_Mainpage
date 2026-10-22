package com.firstory.domain.generation.dto;

import com.firstory.domain.generation.entity.ImageStatus;
import com.firstory.domain.generation.entity.StoryPage;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;

import java.util.List;

/**
 * 미리보기 (REV-01). 그림은 imageUrl(그림 API)로 받는다. 파일 key나 저장 경로는 넣지 않는다.
 *
 * @param characterSheetUrl   캐릭터 시트가 없으면 null
 * @param regenerateRemaining 남은 다시 만들기 횟수
 */
public record PreviewResponse(Long storyId, StoryStatus status, VersionSummary version, String characterSheetUrl,
                              List<Page> pages, int regenerateRemaining) {

    public record VersionSummary(Long id, int versionNo, String title) {
    }

    /**
     * @param imageUrl 그림이 실패한 쪽(FAILED)은 null
     */
    public record Page(int pageNo, String text, ImageStatus imageStatus, String imageUrl) {
    }

    public static PreviewResponse of(Story story, StoryVersion version, List<StoryPage> pages,
                                     int regenerateRemaining) {
        Long storyId = story.getId();
        return new PreviewResponse(storyId, story.getStatus(),
                new VersionSummary(version.getId(), version.getVersionNo(), version.getTitle()),
                version.getCharacterSheetKey() == null ? null
                        : StoryImageUrls.characterSheet(storyId, version.getId()),
                pages.stream().map(page -> new Page(page.getPageNo(), page.getText(), page.getImageStatus(),
                        page.getImageStatus() == ImageStatus.DONE
                                ? StoryImageUrls.page(storyId, version.getId(), page.getPageNo()) : null))
                        .toList(),
                regenerateRemaining);
    }

    // 본문에 부모 입력이 반영되어 있어 로그에 남기지 않는다
    @Override
    public String toString() {
        return "PreviewResponse[storyId=" + storyId + ", status=" + status + "]";
    }
}
