package com.firstory.domain.story.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum StoryStatus {

    DRAFT,
    INTERVIEWING,
    CONFIRMING,
    REQUESTED,
    GENERATING_TEXT,
    GENERATING_IMAGES,
    REVIEWING,
    FAILED,
    COMPLETED,
    BLOCKED;

    private static final Map<StoryStatus, Set<StoryStatus>> TRANSITIONS = new EnumMap<>(StoryStatus.class);

    static {
        TRANSITIONS.put(DRAFT, EnumSet.of(INTERVIEWING, BLOCKED));
        TRANSITIONS.put(INTERVIEWING, EnumSet.of(CONFIRMING, BLOCKED));
        TRANSITIONS.put(CONFIRMING, EnumSet.of(REQUESTED, BLOCKED));
        // REQUESTED → GENERATING_IMAGES: 그림만 다시
        TRANSITIONS.put(REQUESTED, EnumSet.of(GENERATING_TEXT, GENERATING_IMAGES, FAILED));
        TRANSITIONS.put(GENERATING_TEXT, EnumSet.of(GENERATING_IMAGES, FAILED));
        TRANSITIONS.put(GENERATING_IMAGES, EnumSet.of(REVIEWING, FAILED));
        // REVIEWING → REQUESTED: 다시 만들기, → COMPLETED: 확정
        TRANSITIONS.put(REVIEWING, EnumSet.of(REQUESTED, COMPLETED));
        // FAILED → REQUESTED: 다시 시도
        TRANSITIONS.put(FAILED, EnumSet.of(REQUESTED));
        TRANSITIONS.put(COMPLETED, EnumSet.noneOf(StoryStatus.class));
        TRANSITIONS.put(BLOCKED, EnumSet.noneOf(StoryStatus.class));
    }

    public boolean canTransitionTo(StoryStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    public Set<StoryStatus> nextStatuses() {
        return Collections.unmodifiableSet(TRANSITIONS.get(this));
    }

    public boolean isCreating() {
        return this == DRAFT || this == INTERVIEWING || this == CONFIRMING;
    }

    public boolean isGenerating() {
        return this == REQUESTED || this == GENERATING_TEXT || this == GENERATING_IMAGES;
    }
}
