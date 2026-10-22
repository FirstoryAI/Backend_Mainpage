package com.firstory.domain.reading.repository;

import com.firstory.domain.reading.entity.ReadingSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReadingSessionRepository extends JpaRepository<ReadingSession, Long> {

    // 이어 읽기
    Optional<ReadingSession> findTopByStoryIdAndCompletedAtIsNullOrderByStartedAtDesc(Long storyId);

    long countByStoryIdAndCompletedAtIsNotNull(Long storyId);

    /**
     * 세션 id로 들어오는 API용. 세션 → 동화 → 회원까지 확인한다. 지운 동화의 세션은 찾지 않는다.
     * 같은 세션에 동시에 들어온 요청(대화 버튼 두 번 누름)을 차례로 처리하도록 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from ReadingSession r
            join fetch r.story s
            where r.id = :id and s.user.id = :userId and s.deletedAt is null""")
    Optional<ReadingSession> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    // 동화 상세의 마지막 읽은 시각
    Optional<ReadingSession> findTopByStoryIdOrderByStartedAtDesc(Long storyId);
}
