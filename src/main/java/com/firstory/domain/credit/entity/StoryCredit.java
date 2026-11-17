package com.firstory.domain.credit.entity;

import com.firstory.domain.user.entity.User;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * 이용권 한 장. amount회까지 동화를 AI로 처음 만들 수 있다. 쓴 횟수는 StoryCreditUse 개수로 센다.
 */
@Getter
@Entity
@Table(name = "story_credits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryCredit extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreditSource source;

    @Column(nullable = false)
    private int amount;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invite_code_id")
    private InviteCode inviteCode;

    private StoryCredit(User user, CreditSource source, int amount, Instant expiresAt, InviteCode inviteCode) {
        this.user = Objects.requireNonNull(user);
        this.source = Objects.requireNonNull(source);
        this.amount = amount;
        this.expiresAt = expiresAt;
        this.inviteCode = inviteCode;
    }

    public static StoryCredit grant(User user, CreditSource source, int amount, Instant expiresAt,
                                    InviteCode inviteCode) {
        return new StoryCredit(user, source, amount, expiresAt, inviteCode);
    }

    public boolean isActive(Instant now) {
        return expiresAt == null || now.isBefore(expiresAt);
    }

    /**
     * 남은 횟수를 없앤다 (이미 쓴 횟수만 남긴다). 사전 연락 이용권이 기존 무료 1회를 대신할 때.
     */
    public void closeAt(long used) {
        this.amount = (int) Math.min(amount, used);
    }
}
