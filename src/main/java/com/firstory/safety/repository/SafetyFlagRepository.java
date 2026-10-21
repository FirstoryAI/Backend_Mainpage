package com.firstory.safety.repository;

import com.firstory.safety.domain.SafetyFlag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyFlagRepository extends JpaRepository<SafetyFlag, Long> {

    long countByStoryId(Long storyId);
}
