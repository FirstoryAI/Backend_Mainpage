package com.firstory.story.domain;

import com.firstory.global.entity.BaseTimeEntity;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

/**
 * 동화 만들기 입력. stories와 PK를 공유하는 1:1.
 */
@Getter
@Entity
@Table(name = "story_inputs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryInput extends BaseTimeEntity {

    @Id
    @Column(name = "story_id")
    private Long storyId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;

    @Enumerated(EnumType.STRING)
    @Column(name = "situation_timing", length = 20)
    private SituationTiming situationTiming;

    // TODO(PM): 상황 분류 값 목록 미정. 문자열 코드로 저장
    @Column(name = "situation_category", length = 50)
    private String situationCategory;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "moment_reaction", columnDefinition = "text")
    private String momentReaction;

    // TODO(PM): 감정 값 목록 미정. 문자열 코드로 저장
    @Column(length = 30)
    private String emotion;

    @Column(name = "parent_message", columnDefinition = "text")
    private String parentMessage;

    @Column(name = "message_direction", length = 50)
    private String messageDirection;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Tone tone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "child_snapshot", columnDefinition = "jsonb")
    private ChildSnapshot childSnapshot;

    @Column(name = "interview_summary", columnDefinition = "text")
    private String interviewSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_end_reason", length = 20)
    private InterviewEndReason interviewEndReason;

    private StoryInput(Story story) {
        this.story = Objects.requireNonNull(story);
    }

    public static StoryInput begin(Story story) {
        return new StoryInput(story);
    }

    public void writeSituation(SituationTiming timing, String category, String description,
                               String momentReaction, String emotion) {
        if (timing != SituationTiming.PAST && emotion != null && !emotion.isBlank()) {
            throw new BusinessException(ErrorCode.EMOTION_ONLY_FOR_PAST);
        }
        this.situationTiming = timing;
        this.situationCategory = category;
        this.description = description;
        this.momentReaction = momentReaction;
        this.emotion = timing == SituationTiming.PAST ? emotion : null;
    }

    public void writeChild(ChildSnapshot childSnapshot) {
        this.childSnapshot = Objects.requireNonNull(childSnapshot);
    }

    public void writeMessage(String parentMessage, String messageDirection) {
        this.parentMessage = parentMessage;
        this.messageDirection = messageDirection;
    }

    public void chooseTone(Tone tone) {
        this.tone = Objects.requireNonNull(tone);
    }

    public void finishInterview(String interviewSummary, InterviewEndReason reason) {
        this.interviewSummary = interviewSummary;
        this.interviewEndReason = Objects.requireNonNull(reason);
    }
}
