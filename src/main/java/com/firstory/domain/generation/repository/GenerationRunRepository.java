package com.firstory.domain.generation.repository;

import com.firstory.domain.generation.entity.GenerationRun;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GenerationRunRepository extends JpaRepository<GenerationRun, Long> {

    Optional<GenerationRun> findTopByStoryIdOrderByRunNoDesc(Long storyId);

    List<GenerationRun> findAllByStoryIdOrderByRunNoAsc(Long storyId);

    /**
     * 진행을 확인할 실행을 잡는다. 서버가 여러 대여도 같은 실행을 동시에 잡지 않도록 다른 쪽이 잡은 행은 건너뛴다.
     * 잠금은 호출한 트랜잭션이 끝날 때 풀린다 (짧게 잡고 바로 놓는다).
     */
    @Query(value = """
            SELECT * FROM generation_runs
            WHERE status = 'RUNNING'
            ORDER BY started_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<GenerationRun> lockRunning(@Param("limit") int limit);

    /**
     * AI 응답을 저장할 때 다시 읽고 잠근다. 그 사이 다른 쪽이 처리했으면 RUNNING이 아니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GenerationRun r where r.id = :id")
    Optional<GenerationRun> findByIdForUpdate(@Param("id") Long id);
}
