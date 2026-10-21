package com.firstory.generation.repository;

import com.firstory.generation.domain.StoryVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoryVersionRepository extends JpaRepository<StoryVersion, Long> {

    List<StoryVersion> findAllByStoryIdOrderByVersionNoAsc(Long storyId);

    Optional<StoryVersion> findTopByStoryIdOrderByVersionNoDesc(Long storyId);

    Optional<StoryVersion> findByIdAndStoryId(Long id, Long storyId);
}
