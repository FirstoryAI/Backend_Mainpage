package com.firstory.domain.guide.repository;

import com.firstory.domain.guide.entity.GuidePrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GuidePromptRepository extends JpaRepository<GuidePrompt, Long> {

    List<GuidePrompt> findAllByGuideIdOrderBySeqAsc(Long guideId);

    /**
     * 이 버전 가이드의 질문일 때만 찾는다. 다른 동화나 다른 버전의 질문 id로는 찾지 못한다.
     */
    @Query("select p from GuidePrompt p where p.id = :id and p.guide.version.id = :versionId")
    Optional<GuidePrompt> findInVersion(@Param("id") Long id, @Param("versionId") Long versionId);

    @Query("select p from GuidePrompt p where p.guide.version.id = :versionId order by p.seq")
    List<GuidePrompt> findAllByVersionId(@Param("versionId") Long versionId);
}
