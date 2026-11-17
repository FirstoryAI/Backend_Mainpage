package com.firstory.domain.credit.repository;

import com.firstory.domain.credit.entity.StoryCreditUse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StoryCreditUseRepository extends JpaRepository<StoryCreditUse, Long> {

    boolean existsByStoryId(Long storyId);

    /**
     * 이용권별 쓴 횟수 [creditId, count]. 지운 동화도 센다 (story를 조인하지 않는다).
     */
    @Query("select u.credit.id, count(u) from StoryCreditUse u where u.credit.id in :creditIds group by u.credit.id")
    List<Object[]> countByCreditIds(@Param("creditIds") Collection<Long> creditIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from StoryCreditUse u where u.story.id = :storyId")
    int deleteByStoryId(@Param("storyId") Long storyId);
}
