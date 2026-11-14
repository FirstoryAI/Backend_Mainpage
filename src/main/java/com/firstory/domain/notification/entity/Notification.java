package com.firstory.domain.notification.entity;

import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.global.entity.BaseTimeEntity;
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

import java.util.Objects;

/**
 * 이메일 알림. 같은 실행에 같은 종류의 알림은 한 번만 보낸다 (generation_run_id, type) UNIQUE.
 * 다시 만들기나 재시도는 새 실행이라 다시 보낼 수 있다.
 * EMAIL_NOTIFY 동의 확인은 서비스의 책임이다.
 */
@Getter
@Entity
@Table(name = "notifications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"generation_run_id", "type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generation_run_id", nullable = false)
    private GenerationRun generationRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "fail_reason")
    private String failReason;

    private Notification(GenerationRun generationRun, NotificationType type) {
        this.generationRun = Objects.requireNonNull(generationRun);
        this.type = Objects.requireNonNull(type);
        this.status = NotificationStatus.PENDING;
    }

    /**
     * 받는 회원과 동화는 실행(generation_runs)에서 찾는다. 보낸 시각은 updated_at (새 ERD).
     */
    public static Notification pending(GenerationRun run, NotificationType type) {
        return new Notification(run, type);
    }

    public void sent() {
        this.status = NotificationStatus.SENT;
    }

    public void failed(String reason) {
        this.status = NotificationStatus.FAILED;
        // fail_reason은 varchar(255)
        this.failReason = reason != null && reason.length() > 255 ? reason.substring(0, 255) : reason;
    }
}
