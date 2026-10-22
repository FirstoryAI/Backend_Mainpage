package com.firstory.domain.story.entity;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 서재 탭 (LIB-01). BLOCKED 동화는 어느 탭에도 보이지 않는다.
 *
 * <pre>
 * ALL        BLOCKED를 뺀 전부
 * CREATING   DRAFT, INTERVIEWING, CONFIRMING, REQUESTED, GENERATING_TEXT, GENERATING_IMAGES
 * REVIEWING  REVIEWING
 * UNREAD     COMPLETED + 함께 읽은 적 없음
 * READ       COMPLETED + 함께 읽은 적 있음
 * FAILED     FAILED
 * </pre>
 */
@Getter
public enum LibraryFilter {

    ALL(EnumSet.complementOf(EnumSet.of(StoryStatus.BLOCKED)), Read.ANY),
    CREATING(EnumSet.of(StoryStatus.DRAFT, StoryStatus.INTERVIEWING, StoryStatus.CONFIRMING, StoryStatus.REQUESTED,
            StoryStatus.GENERATING_TEXT, StoryStatus.GENERATING_IMAGES), Read.ANY),
    REVIEWING(EnumSet.of(StoryStatus.REVIEWING), Read.ANY),
    UNREAD(EnumSet.of(StoryStatus.COMPLETED), Read.UNREAD),
    READ(EnumSet.of(StoryStatus.COMPLETED), Read.READ),
    FAILED(EnumSet.of(StoryStatus.FAILED), Read.ANY);

    private final Set<StoryStatus> statuses;
    private final Read read;

    LibraryFilter(Set<StoryStatus> statuses, Read read) {
        this.statuses = Collections.unmodifiableSet(statuses);
        this.read = read;
    }

    public enum Read {
        ANY,
        READ,
        UNREAD
    }

    public boolean onlyRead() {
        return read == Read.READ;
    }

    public boolean onlyUnread() {
        return read == Read.UNREAD;
    }
}
