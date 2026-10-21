package com.firstory.guide.repository;

import com.firstory.guide.domain.GuidePrompt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuidePromptRepository extends JpaRepository<GuidePrompt, Long> {

    List<GuidePrompt> findAllByGuideIdOrderBySeqAsc(Long guideId);
}
