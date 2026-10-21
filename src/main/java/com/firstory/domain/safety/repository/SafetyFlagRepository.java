package com.firstory.domain.safety.repository;

import com.firstory.domain.safety.entity.SafetyFlag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyFlagRepository extends JpaRepository<SafetyFlag, Long> {

    long countByStoryId(Long storyId);
}
