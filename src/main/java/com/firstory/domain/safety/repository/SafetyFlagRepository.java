package com.firstory.domain.safety.repository;

import com.firstory.domain.safety.entity.SafetyFlag;
import com.firstory.domain.safety.entity.SafetyLevel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyFlagRepository extends JpaRepository<SafetyFlag, Long> {

    /**
     * 위기 신호 횟수는 동화 단위, 단계별로 센다. 차단 판단에는 REVISE 횟수만 쓴다.
     */
    long countByStoryIdAndLevel(Long storyId, SafetyLevel level);
}
