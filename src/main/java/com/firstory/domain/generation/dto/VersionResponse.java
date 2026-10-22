package com.firstory.domain.generation.dto;

import com.firstory.domain.generation.entity.RegenerateScope;
import com.firstory.domain.generation.entity.StoryVersion;

import java.time.Instant;

/**
 * 버전 목록의 한 줄. 버전은 parentVersionNo로 나무 구조를 이룬다.
 *
 * @param parentVersionNo 어느 버전에서 다시 만들었는지. 처음 만든 버전이면 null
 * @param scope           이 버전을 만든 범위 (처음 만들기 ALL)
 * @param current         지금 보여 주는 버전인지
 */
public record VersionResponse(Long id, int versionNo, Integer parentVersionNo, String title, RegenerateScope scope,
                              Instant createdAt, boolean current) {

    /**
     * parentVersion, run을 읽으므로 트랜잭션 안에서 부른다.
     */
    public static VersionResponse of(StoryVersion version, Long currentVersionId) {
        StoryVersion parent = version.getParentVersion();
        return new VersionResponse(version.getId(), version.getVersionNo(),
                parent == null ? null : parent.getVersionNo(), version.getTitle(), version.getRun().getScope(),
                version.getCreatedAt(), version.getId().equals(currentVersionId));
    }
}
