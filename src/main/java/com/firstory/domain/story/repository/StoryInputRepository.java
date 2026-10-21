package com.firstory.domain.story.repository;

import com.firstory.domain.story.entity.StoryInput;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryInputRepository extends JpaRepository<StoryInput, Long> {
}
