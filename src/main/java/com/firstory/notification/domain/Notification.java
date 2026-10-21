package com.firstory.notification.domain;

import com.firstory.generation.domain.GenerationRun;
import com.firstory.global.entity.BaseTimeEntity;
import com.firstory.story.domain.Story;
import com.firstory.user.domain.User;
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

/**
 * 문자 알림. 같은 실행에 같은 종류의 알림은 한 번만 보낸다 (generation_run_id, type) UNIQUE.
 * 다시 만들기나 재시도는 새 실행이라 다시 보낼 수 있다.
 * SMS_NOTIFY 동의 확인은 서비스의 책임이다.
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
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generation_run_id", nullable = false)
    private GenerationRun generationRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "template_code", nullable = false, length = 50)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "provider_msg_id", length = 100)
    private String providerMsgId;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    private Notification(User user, Story story, GenerationRun generationRun, NotificationType type,
                         NotificationChannel channel, String templateCode) {
        this.user = Objects.requireNonNull(user);
        this.story = Objects.requireNonNull(story);
        this.generationRun = Objects.requireNonNull(generationRun);
        this.type = Objects.requireNonNull(type);
        this.channel = Objects.requireNonNull(channel);
        this.templateCode = Objects.requireNonNull(templateCode);
        this.status = NotificationStatus.PENDING;
    }

    public static Notification pending(User user, Story story, GenerationRun run, NotificationType type,
                                       NotificationChannel channel, String templateCode) {
        return new Notification(user, story, run, type, channel, templateCode);
    }

    public void sent(String providerMsgId, Instant now) {
        this.status = NotificationStatus.SENT;
        this.providerMsgId = providerMsgId;
        this.sentAt = now;
    }

    public void failed(String reason) {
        this.status = NotificationStatus.FAILED;
        // fail_reason은 varchar(255)
        this.failReason = reason != null && reason.length() > 255 ? reason.substring(0, 255) : reason;
    }
}
