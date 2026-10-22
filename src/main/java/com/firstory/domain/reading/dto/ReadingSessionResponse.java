package com.firstory.domain.reading.dto;

import com.firstory.domain.reading.entity.ReadingSession;

import java.time.Instant;

/**
 * @param lastPage 마지막으로 본 쪽. 0이면 아직 표지
 */
public record ReadingSessionResponse(Long sessionId, int lastPage, Instant startedAt) {

    public static ReadingSessionResponse of(ReadingSession session) {
        return new ReadingSessionResponse(session.getId(), session.getLastPage(), session.getStartedAt());
    }
}
