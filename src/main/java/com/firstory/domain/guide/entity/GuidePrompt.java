package com.firstory.domain.guide.entity;

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

import java.util.Objects;

/**
 * 읽는 중간에 보여 주는 대화 질문.
 * AI 결과(04-guide.json)에서는 질문 본문 필드명이 text다. 매핑은 DTO에서 한다.
 */
@Getter
@Entity
@Table(name = "guide_prompts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"guide_id", "seq"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuidePrompt extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guide_id", nullable = false)
    private ReadingGuide guide;

    @Column(nullable = false)
    private int seq;

    @Column(name = "after_page", nullable = false)
    private int afterPage;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(length = 50)
    private String purpose;

    @Column(name = "parent_hint", columnDefinition = "text")
    private String parentHint;

    private GuidePrompt(ReadingGuide guide, int seq, int afterPage, String question, String purpose,
                        String parentHint) {
        if (afterPage < 1 || afterPage > 10) {
            throw new IllegalArgumentException("질문 위치는 1~10쪽 사이여야 해요: " + afterPage);
        }
        this.guide = Objects.requireNonNull(guide);
        this.seq = seq;
        this.afterPage = afterPage;
        this.question = Objects.requireNonNull(question);
        this.purpose = purpose;
        this.parentHint = parentHint;
    }

    public static GuidePrompt create(ReadingGuide guide, int seq, int afterPage, String question, String purpose,
                                     String parentHint) {
        return new GuidePrompt(guide, seq, afterPage, question, purpose, parentHint);
    }
}
