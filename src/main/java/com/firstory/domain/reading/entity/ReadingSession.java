package com.firstory.domain.reading.entity;

import com.firstory.domain.generation.entity.StoryPage;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.story.entity.Story;
import com.firstory.global.entity.BaseTimeEntity;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import jakarta.persistence.Column;
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

import java.time.Instant;
import java.util.Objects;

/**
 * 함께 읽기 한 번. 완료 시 Story.markFirstReadCompleted 호출은 서비스의 책임이다.
 */
@Getter
@Entity
@Table(name = "reading_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private StoryVersion version;

    // 0이면 아직 표지
    @Column(name = "last_page", nullable = false)
    private int lastPage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private ReadingSession(Story story, StoryVersion version, Instant now) {
        this.story = Objects.requireNonNull(story);
        this.version = Objects.requireNonNull(version);
        this.lastPage = 0;
        this.startedAt = Objects.requireNonNull(now);
    }

    public static ReadingSession start(Story story, StoryVersion version, Instant now) {
        return new ReadingSession(story, version, now);
    }

    public void moveTo(int page) {
        if (page < StoryPage.FIRST_PAGE || page > StoryPage.LAST_PAGE) {
            throw new BusinessException(ErrorCode.PAGE_OUT_OF_RANGE);
        }
        this.lastPage = page;
    }

    public void complete(Instant now) {
        if (completedAt == null) {
            completedAt = now;
        }
    }

    public boolean isCompleted() {
        return completedAt != null;
    }
}
