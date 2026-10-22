package com.firstory.domain.generation.repository;

import com.firstory.domain.generation.entity.StoryPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoryPageRepository extends JpaRepository<StoryPage, Long> {

    List<StoryPage> findAllByVersionIdOrderByPageNoAsc(Long versionId);

    Optional<StoryPage> findByVersionIdAndPageNo(Long versionId, int pageNo);
}
