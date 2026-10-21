package com.firstory.domain.story.entity;

import com.firstory.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Getter
@Entity
@Table(name = "interview_turns",
        uniqueConstraints = @UniqueConstraint(columnNames = {"story_id", "turn_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewTurn extends BaseTimeEntity {

    public static final int MAX_TURNS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(name = "turn_no", nullable = false)
    private int turnNo;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    // null이면 아직 답하지 않음
    @Column(columnDefinition = "text")
    private String answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_update", columnDefinition = "jsonb")
    private Map<String, Object> contextUpdate;

    @Column(name = "answered_at")
    private Instant answeredAt;

    private InterviewTurn(Story story, int turnNo, String question) {
        if (turnNo < 1 || turnNo > MAX_TURNS) {
            throw new IllegalArgumentException("인터뷰 차례는 1~" + MAX_TURNS + " 사이여야 해요: " + turnNo);
        }
        this.story = Objects.requireNonNull(story);
        this.turnNo = turnNo;
        this.question = Objects.requireNonNull(question);
    }

    public static InterviewTurn ask(Story story, int turnNo, String question) {
        return new InterviewTurn(story, turnNo, question);
    }

    public void answer(String text, Instant now) {
        this.answer = Objects.requireNonNull(text);
        this.answeredAt = now;
    }

    public void updateContext(Map<String, Object> contextUpdate) {
        this.contextUpdate = contextUpdate == null ? null : new LinkedHashMap<>(contextUpdate);
    }

    public boolean isAnswered() {
        return answer != null;
    }
}
