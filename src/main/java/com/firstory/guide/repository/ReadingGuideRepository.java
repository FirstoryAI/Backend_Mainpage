package com.firstory.guide.repository;

import com.firstory.guide.domain.ReadingGuide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReadingGuideRepository extends JpaRepository<ReadingGuide, Long> {

    Optional<ReadingGuide> findByVersionId(Long versionId);
}
