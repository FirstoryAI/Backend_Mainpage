package com.firstory.generation.repository;

import com.firstory.generation.domain.GenerationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GenerationRunRepository extends JpaRepository<GenerationRun, Long> {

    Optional<GenerationRun> findTopByStoryIdOrderByRunNoDesc(Long storyId);

    List<GenerationRun> findAllByStoryIdOrderByRunNoAsc(Long storyId);
}
