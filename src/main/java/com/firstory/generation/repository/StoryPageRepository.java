package com.firstory.generation.repository;

import com.firstory.generation.domain.StoryPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoryPageRepository extends JpaRepository<StoryPage, Long> {

    List<StoryPage> findAllByVersionIdOrderByPageNoAsc(Long versionId);
}
