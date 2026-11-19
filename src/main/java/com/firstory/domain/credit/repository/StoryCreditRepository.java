package com.firstory.domain.credit.repository;

import com.firstory.domain.credit.entity.StoryCredit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StoryCreditRepository extends JpaRepository<StoryCredit, Long> {

    List<StoryCredit> findByUserIdOrderByIdAsc(Long userId);

    /**
     * 생성 확정에서 남은 횟수를 보고 쓰는 동안 같은 회원의 다른 확정을 기다리게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StoryCredit c where c.user.id = :userId order by c.id")
    List<StoryCredit> findByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * 한 회원의 이용권을 쓴 기록째 다른 회원에게 옮긴다 (탈퇴 후 같은 이메일로 재가입).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE story_credits SET user_id = :to, updated_at = :now WHERE user_id = :from",
            nativeQuery = true)
    int moveToUser(@Param("from") Long fromUserId, @Param("to") Long toUserId, @Param("now") Instant now);
}
