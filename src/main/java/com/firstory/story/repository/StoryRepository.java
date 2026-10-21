package com.firstory.story.repository;

import com.firstory.story.domain.Story;
import com.firstory.story.domain.StoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoryRepository extends JpaRepository<Story, Long> {

    Optional<Story> findByIdAndUserId(Long id, Long userId);

    Optional<Story> findByThreadId(String threadId);

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
