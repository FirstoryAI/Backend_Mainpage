package com.firstory.domain.generation.entity;

import com.firstory.domain.story.entity.Story;
import com.firstory.global.entity.BaseTimeEntity;
import com.firstory.global.entity.CustomText;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 동화 확정 전 피드백 (REV-04 팝업). 동화마다 하나, 확정한 버전과 함께 남긴다.
 */
@Getter
@Entity
@Table(name = "story_feedbacks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryFeedback extends BaseTimeEntity {

    public static final int MAX_COMMENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false, unique = true)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private StoryVersion version;

    @Enumerated(EnumType.STRING)
    @Column(name = "situation_reflection", nullable = false, length = 30)
    private SituationReflection situationReflection;

    @Enumerated(EnumType.STRING)
    @Column(name = "story_satisfaction", nullable = false, length = 30)
    private StorySatisfaction storySatisfaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_intent", nullable = false, length = 30)
    private ReadingIntent readingIntent;

    // 비었으면 null
    @Column(length = MAX_COMMENT_LENGTH)
    private String comment;

    private StoryFeedback(Story story, StoryVersion version, SituationReflection situationReflection,
                          StorySatisfaction storySatisfaction, ReadingIntent readingIntent, String comment) {
        this.story = Objects.requireNonNull(story);
        this.version = Objects.requireNonNull(version);
        this.situationReflection = Objects.requireNonNull(situationReflection);
        this.storySatisfaction = Objects.requireNonNull(storySatisfaction);
        this.readingIntent = Objects.requireNonNull(readingIntent);
        this.comment = CustomText.strip(comment);
    }

    /**
     * @param version 확정한 버전 (Story.currentVersion)
     * @param comment 앞뒤 공백을 지우고 비었으면 null로 둔다
     */
    public static StoryFeedback record(Story story, StoryVersion version, SituationReflection situationReflection,
                                       StorySatisfaction storySatisfaction, ReadingIntent readingIntent,
                                       String comment) {
        return new StoryFeedback(story, version, situationReflection, storySatisfaction, readingIntent, comment);
    }
}
