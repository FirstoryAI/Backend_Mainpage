package com.firstory.story.repository;

import com.firstory.story.domain.StoryInput;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryInputRepository extends JpaRepository<StoryInput, Long> {
}
