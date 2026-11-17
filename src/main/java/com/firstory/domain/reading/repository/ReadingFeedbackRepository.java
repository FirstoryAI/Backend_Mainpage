package com.firstory.domain.reading.repository;

import com.firstory.domain.reading.entity.ReadingFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReadingFeedbackRepository extends JpaRepository<ReadingFeedback, Long> {

    boolean existsBySessionId(Long sessionId);

    /**
     * 이 회원이 읽기 완료 피드백을 한 번이라도 냈는지 (Backend#82). 지운 동화에 낸 것도 센다
     * (native라 Story의 deleted_at 제한이 붙지 않는다).
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM reading_feedbacks f JOIN stories s ON s.id = f.story_id
                           WHERE s.user_id = :userId)""", nativeQuery = true)
    boolean existsByUserId(@Param("userId") Long userId);
}
