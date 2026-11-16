package com.firstory.domain.reading.repository;

import com.firstory.domain.reading.entity.ReadingFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingFeedbackRepository extends JpaRepository<ReadingFeedback, Long> {

    boolean existsBySessionId(Long sessionId);
}
