package com.firstory.domain.story.dto;

import com.firstory.domain.story.entity.StoryStatus;

public record StoryCreatedResponse(Long storyId, StoryStatus status) {
}
