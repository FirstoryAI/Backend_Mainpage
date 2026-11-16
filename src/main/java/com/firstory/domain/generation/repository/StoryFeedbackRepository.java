package com.firstory.domain.generation.repository;

import com.firstory.domain.generation.entity.StoryFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryFeedbackRepository extends JpaRepository<StoryFeedback, Long> {
}
