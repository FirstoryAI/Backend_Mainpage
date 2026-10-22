package com.firstory.domain.safety.repository;

import com.firstory.domain.safety.entity.SafetyFlag;
import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetySource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyFlagRepository extends JpaRepository<SafetyFlag, Long> {

    /**
     * 위기 신호 횟수는 동화 단위, 단계별로 센다. 차단 판단에는 REVISE 횟수만 쓴다.
     */
    long countByStoryIdAndLevel(Long storyId, SafetyLevel level);

    /**
     * 부모 책임인 위기 신호만 센다. AI가 만든 결과(OUTPUT)는 차단 판단에 넣지 않는다.
     * occurrenceNo는 UNIQUE(story, level, occurrence_no)라 OUTPUT까지 포함한 countByStoryIdAndLevel로 정한다.
     */
    long countByStoryIdAndLevelAndSourceNot(Long storyId, SafetyLevel level, SafetySource source);
}
