package com.firstory.domain.story.repository;

import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoryRepository extends JpaRepository<Story, Long> {

    Optional<Story> findByIdAndUserId(Long id, Long userId);

    Optional<Story> findByThreadId(String threadId);

    /**
     * AI 호출 뒤 결과를 저장할 때 쓴다. 같은 동화에 동시에 들어온 요청(위기 신호 순번, 인터뷰 차례)을 차례로 처리한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Story s where s.id = :id")
    Optional<Story> findByIdForUpdate(@Param("id") Long id);

    List<Story> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<Story> findAllByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, Collection<StoryStatus> statuses);

    /**
     * 아이 프로필 삭제 전에 서비스에서 먼저 호출한다.
     * 삭제된 Child는 @SQLRestriction 때문에 지연 로딩하면 예외가 나므로 연결을 끊어 둔다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Story s set s.child = null where s.child.id = :childId")
    int detachChild(@Param("childId") Long childId);
}
