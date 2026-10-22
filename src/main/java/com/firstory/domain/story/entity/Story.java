package com.firstory.domain.story.entity;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.user.entity.User;
import com.firstory.global.entity.BaseTimeEntity;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
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
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.Objects;

@Getter
@Entity
@Table(name = "stories")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Story extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // "다음에도 이 아이 정보로 만들기"를 체크하지 않았으면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id")
    private Child child;

    @Column(name = "thread_id", nullable = false, unique = true, length = 64)
    private String threadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StoryStatus status;

    // 되돌리기와 앞으로 가기는 이 값만 바꾼다. 버전은 지우지 않는다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private StoryVersion currentVersion;

    // 현재 버전 제목 복사본 (서재 목록용)
    @Column(length = 100)
    private String title;

    @Column(name = "regenerate_count", nullable = false)
    private int regenerateCount;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "first_read_completed_at")
    private Instant firstReadCompletedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Story(User user, Child child, String threadId) {
        this.user = Objects.requireNonNull(user);
        this.child = child;
        this.threadId = Objects.requireNonNull(threadId);
        this.status = StoryStatus.DRAFT;
    }

    public static Story start(User user, Child child, String threadId) {
        return new Story(user, child, threadId);
    }

    public void changeStatus(StoryStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = next;
    }

    /**
     * 보여 줄 버전을 바꾼다. 새 버전 표시, 되돌리기, 앞으로 가기 모두 이 메서드를 쓴다.
     * 다시 만들기 횟수에는 포함하지 않는다.
     */
    public void showVersion(StoryVersion version) {
        if (version == null || !version.belongsTo(this)) {
            throw new BusinessException(ErrorCode.VERSION_NOT_IN_STORY);
        }
        if (status != StoryStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.currentVersion = version;
        this.title = version.getTitle();
    }

    /**
     * 다시 만들기. 확인 중(REVIEWING)일 때만 가능하고 횟수 한도를 넘을 수 없다.
     * 실패 후 다시 시도는 retry()를 쓴다.
     */
    public void requestRegeneration(int maxRegenerations) {
        if (status != StoryStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (regenerateCount >= maxRegenerations) {
            throw new BusinessException(ErrorCode.REGENERATION_LIMIT_REACHED);
        }
        changeStatus(StoryStatus.REQUESTED);
        regenerateCount++;
    }

    /**
     * 실패 후 다시 시도 (FAILED → REQUESTED). 다시 만들기 횟수에 포함하지 않는다.
     */
    public void retry() {
        if (status != StoryStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        changeStatus(StoryStatus.REQUESTED);
    }

    public void confirm(Instant now) {
        changeStatus(StoryStatus.COMPLETED);
        this.confirmedAt = now;
    }

    public void block() {
        changeStatus(StoryStatus.BLOCKED);
    }

    public void markFirstReadCompleted(Instant now) {
        if (firstReadCompletedAt == null) {
            firstReadCompletedAt = now;
        }
    }

    /**
     * 동화 만들기(CRT-03)에서 고른 아이 프로필. 저장하지 않았으면 null.
     */
    public void assignChild(Child child) {
        this.child = child;
    }

    public void detachChild() {
        this.child = null;
    }

    public void delete(Instant now) {
        if (deletedAt == null) {
            deletedAt = now;
        }
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId() != null && user.getId().equals(userId);
    }

    public String libraryLabel() {
        return switch (status) {
            case DRAFT, INTERVIEWING, CONFIRMING -> "만들던 이야기";
            case REQUESTED, GENERATING_TEXT, GENERATING_IMAGES -> "만드는 중";
            case REVIEWING -> "확인을 기다려요";
            case COMPLETED -> firstReadCompletedAt == null ? "아직 안 읽었어요" : "함께 읽었어요";
            case FAILED -> "만들지 못했어요";
            case BLOCKED -> "만들 수 없는 이야기";
        };
    }
}
