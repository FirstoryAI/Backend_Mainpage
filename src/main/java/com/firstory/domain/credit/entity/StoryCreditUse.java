package com.firstory.domain.credit.entity;

import com.firstory.domain.story.entity.Story;
import com.firstory.global.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
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
 * 이용권 1회 사용 = 동화 한 편 (story_id UNIQUE). 동화를 지워도 남는다.
 * 생성 확정에서 AI를 부르기 전에 남기고, AI가 받지 못하면 지운다 (GenerationService.confirm).
 */
@Getter
@Entity
@Table(name = "story_credit_uses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryCreditUse extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false)
    private StoryCredit credit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false, unique = true)
    private Story story;

    private StoryCreditUse(StoryCredit credit, Story story) {
        this.credit = Objects.requireNonNull(credit);
        this.story = Objects.requireNonNull(story);
    }

    public static StoryCreditUse of(StoryCredit credit, Story story) {
        return new StoryCreditUse(credit, story);
    }
}
