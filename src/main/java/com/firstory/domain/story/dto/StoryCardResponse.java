package com.firstory.domain.story.dto;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.generation.dto.StoryImageUrls;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;

import java.time.Instant;

/**
 * 서재와 홈의 동화 카드.
 *
 * @param title            버전이 아직 없으면 null (프론트가 "만들던 이야기"로 표시)
 * @param label            Story.libraryLabel()
 * @param categoryLabel    상황 분류 (StoryInput.categoryLabel). 기타면 직접 입력한 분류, 상황을 아직 받지 않았으면 null
 * @param coverUrl         현재 버전의 캐릭터 시트 그림 주소. 버전이나 시트가 없으면 null
 * @param childDisplayName 저장된 아이 프로필의 호칭 (부모 화면용). 저장하지 않았으면 null
 */
public record StoryCardResponse(Long id, String title, StoryStatus status, String label, String categoryLabel,
                                String coverUrl, String childDisplayName, Instant createdAt, Instant updatedAt) {

    /**
     * currentVersion, child를 읽으므로 페치 조인한 Story로 부른다.
     */
    public static StoryCardResponse of(Story story, String categoryLabel) {
        StoryVersion version = story.getCurrentVersion();
        return new StoryCardResponse(story.getId(), version == null ? null : story.getTitle(), story.getStatus(),
                story.libraryLabel(), categoryLabel, coverUrl(story), displayName(story.getChild()),
                story.getCreatedAt(), story.getUpdatedAt());
    }

    public static String coverUrl(Story story) {
        StoryVersion version = story.getCurrentVersion();
        return version == null ? null : StoryImageUrls.coverOrSheet(story.getId(), version);
    }

    public static String displayName(Child child) {
        return child == null ? null : child.getDisplayName();
    }
}
