package com.firstory.safety.domain;

import com.firstory.global.entity.BaseTimeEntity;
import com.firstory.story.domain.InterviewTurn;
import com.firstory.story.domain.Story;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Getter
@Entity
@Table(name = "safety_flags",
        uniqueConstraints = @UniqueConstraint(columnNames = {"story_id", "occurrence_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyFlag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SafetySource source;

    // 인터뷰 답변에서 나온 경우만 채운다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_turn_id")
    private InterviewTurn interviewTurn;

    // AI가 분류한 값
    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "ai_reason", columnDefinition = "text")
    private String aiReason;

    // 이 동화에서 몇 번째
    @Column(name = "occurrence_no", nullable = false)
    private int occurrenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_response", length = 20)
    private SafetyUserResponse userResponse;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    private SafetyFlag(Story story, SafetySource source, InterviewTurn interviewTurn, String category,
                       String aiReason, int occurrenceNo, Instant now) {
        if (occurrenceNo < 1) {
            throw new IllegalArgumentException("위기 신호 횟수는 1 이상이어야 해요: " + occurrenceNo);
        }
        this.story = Objects.requireNonNull(story);
        this.source = Objects.requireNonNull(source);
        this.interviewTurn = interviewTurn;
        this.category = Objects.requireNonNull(category);
        this.aiReason = aiReason;
        this.occurrenceNo = occurrenceNo;
        this.detectedAt = Objects.requireNonNull(now);
    }

    public static SafetyFlag record(Story story, SafetySource source, InterviewTurn interviewTurn, String category,
                                    String aiReason, int occurrenceNo, Instant now) {
        return new SafetyFlag(story, source, interviewTurn, category, aiReason, occurrenceNo, now);
    }

    public void respond(SafetyUserResponse response) {
        this.userResponse = Objects.requireNonNull(response);
    }

    public SafetyAction action() {
        return SafetyPolicy.decide(occurrenceNo);
    }
}
