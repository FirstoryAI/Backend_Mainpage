package com.firstory.domain.story.repository;

import com.firstory.domain.story.entity.InterviewTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewTurnRepository extends JpaRepository<InterviewTurn, Long> {

    List<InterviewTurn> findAllByStoryIdOrderByTurnNoAsc(Long storyId);

    Optional<InterviewTurn> findTopByStoryIdOrderByTurnNoDesc(Long storyId);
}
