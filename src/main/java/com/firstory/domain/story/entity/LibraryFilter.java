package com.firstory.domain.story.entity;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 서재 탭 (LIB-01). BLOCKED 동화는 어느 탭에도 보이지 않는다.
 * 만들다 멈춘 임시저장 동화(DRAFT, INTERVIEWING, CONFIRMING)는 서재 탭에 나오지 않고,
 * 홈의 이어 만들기 목록이 DRAFT 필터로 받는다.
 *
 * <pre>
 * ALL        BLOCKED와 임시저장을 뺀 전부
 * CREATING   REQUESTED, GENERATING_TEXT, GENERATING_IMAGES (실제로 만드는 중)
 * REVIEWING  REVIEWING
 * UNREAD     COMPLETED + 함께 읽은 적 없음
 * READ       COMPLETED + 함께 읽은 적 있음
 * FAILED     FAILED
 * DRAFT      DRAFT, INTERVIEWING, CONFIRMING (이어 만들기 목록용, 서재 탭 아님)
 * </pre>
 */
@Getter
public enum LibraryFilter {

    ALL(EnumSet.complementOf(EnumSet.of(StoryStatus.BLOCKED, StoryStatus.DRAFT, StoryStatus.INTERVIEWING,
            StoryStatus.CONFIRMING)), Read.ANY),
    CREATING(EnumSet.of(StoryStatus.REQUESTED, StoryStatus.GENERATING_TEXT, StoryStatus.GENERATING_IMAGES), Read.ANY),
    REVIEWING(EnumSet.of(StoryStatus.REVIEWING), Read.ANY),
    UNREAD(EnumSet.of(StoryStatus.COMPLETED), Read.UNREAD),
    READ(EnumSet.of(StoryStatus.COMPLETED), Read.READ),
    FAILED(EnumSet.of(StoryStatus.FAILED), Read.ANY),
    DRAFT(EnumSet.of(StoryStatus.DRAFT, StoryStatus.INTERVIEWING, StoryStatus.CONFIRMING), Read.ANY);

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
