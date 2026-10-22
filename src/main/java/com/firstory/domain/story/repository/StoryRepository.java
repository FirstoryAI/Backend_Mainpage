package com.firstory.domain.story.repository;

import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * 서재 목록 (LIB-01). 현재 버전과 아이를 함께 읽어 목록 크기와 무관하게 쿼리 수가 일정하다.
     * 정렬은 Pageable로 받는다 (updatedAt DESC, id DESC).
     *
     * @param onlyRead   true면 함께 읽은 것만 (firstReadCompletedAt 있음)
     * @param onlyUnread true면 아직 안 읽은 것만
     */
    @Query(value = """
            select s from Story s
            left join fetch s.currentVersion
            left join fetch s.child
            where s.user.id = :userId and s.status in :statuses
              and (:onlyRead = false or s.firstReadCompletedAt is not null)
              and (:onlyUnread = false or s.firstReadCompletedAt is null)""",
            countQuery = """
                    select count(s) from Story s
                    where s.user.id = :userId and s.status in :statuses
                      and (:onlyRead = false or s.firstReadCompletedAt is not null)
                      and (:onlyUnread = false or s.firstReadCompletedAt is null)""")
    Page<Story> findLibrary(@Param("userId") Long userId, @Param("statuses") Collection<StoryStatus> statuses,
                            @Param("onlyRead") boolean onlyRead, @Param("onlyUnread") boolean onlyUnread,
                            Pageable pageable);

    /**
     * 홈의 최근 완성 동화. 최근 확정 순.
     */
    @Query("""
            select s from Story s
            left join fetch s.currentVersion
            left join fetch s.child
            where s.user.id = :userId and s.status = com.firstory.domain.story.entity.StoryStatus.COMPLETED
            order by s.confirmedAt desc, s.id desc""")
    List<Story> findRecentCompleted(@Param("userId") Long userId, Pageable pageable);

    /**
     * 상태별 개수와 그중 아직 안 읽은 수. [status, count, unreadCount]
     */
    @Query("""
            select s.status, count(s), sum(case when s.firstReadCompletedAt is null then 1 else 0 end)
            from Story s
            where s.user.id = :userId
            group by s.status""")
    List<Object[]> countByStatus(@Param("userId") Long userId);

    /**
     * 동화 상세 (LIB-02). 현재 버전과 아이를 함께 읽는다.
     */
    @Query("""
            select s from Story s
            left join fetch s.currentVersion
            left join fetch s.child
            where s.id = :id and s.user.id = :userId""")
    Optional<Story> findDetail(@Param("id") Long id, @Param("userId") Long userId);

    List<Story> findAllByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, Collection<StoryStatus> statuses);

    /**
     * 아이 프로필 삭제 전에 서비스에서 먼저 호출한다.
     * 삭제된 Child는 @SQLRestriction 때문에 지연 로딩하면 예외가 나므로 연결을 끊어 둔다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Story s set s.child = null where s.child.id = :childId")
    int detachChild(@Param("childId") Long childId);
}
