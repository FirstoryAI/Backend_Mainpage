package com.firstory.domain.credit.repository;

import com.firstory.domain.credit.entity.StoryCredit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoryCreditRepository extends JpaRepository<StoryCredit, Long> {

    List<StoryCredit> findByUserIdOrderByIdAsc(Long userId);

    /**
     * 생성 확정에서 남은 횟수를 보고 쓰는 동안 같은 회원의 다른 확정을 기다리게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StoryCredit c where c.user.id = :userId order by c.id")
    List<StoryCredit> findByUserIdForUpdate(@Param("userId") Long userId);
}
