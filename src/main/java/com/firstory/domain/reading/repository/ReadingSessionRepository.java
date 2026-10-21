package com.firstory.domain.reading.repository;

import com.firstory.domain.reading.entity.ReadingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReadingSessionRepository extends JpaRepository<ReadingSession, Long> {

    // 이어 읽기
    Optional<ReadingSession> findTopByStoryIdAndCompletedAtIsNullOrderByStartedAtDesc(Long storyId);

    long countByStoryIdAndCompletedAtIsNotNull(Long storyId);
}
