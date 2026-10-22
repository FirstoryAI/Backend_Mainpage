package com.firstory.domain.generation.dto;

import com.firstory.domain.story.entity.StoryStatus;

import java.time.Instant;

public record ApproveResponse(Long storyId, StoryStatus status, Instant confirmedAt) {
}
