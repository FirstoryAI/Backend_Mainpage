package com.firstory.domain.user.entity;

import com.firstory.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Getter
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    public static final int MAX_LOGIN_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // UNIQUE는 DB 부분 인덱스(ux_users_email_active)가 담당한다
    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "phone_enc", nullable = false)
    private String phoneEnc;

    // UNIQUE는 DB 부분 인덱스(ux_users_phone_hash_active)가 담당한다
    @Column(name = "phone_hash", nullable = false, length = 64)
    private String phoneHash;

    @Column(name = "phone_verified_at", nullable = false)
    private Instant phoneVerifiedAt;

    @Column(name = "onboarding_seen_at")
    private Instant onboardingSeenAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    // TODO(PM): 탈퇴 시 개인정보 파기 시점 미정 (PRD 36)
    @Column(name = "deleted_at")
    private Instant deletedAt;

    private User(String email, String passwordHash, String phoneEnc, String phoneHash, Instant phoneVerifiedAt) {
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.phoneEnc = Objects.requireNonNull(phoneEnc);
        this.phoneHash = Objects.requireNonNull(phoneHash);
        this.phoneVerifiedAt = Objects.requireNonNull(phoneVerifiedAt);
    }

    public static User signUp(String email, String passwordHash, String phoneEnc, String phoneHash,
                              Instant phoneVerifiedAt) {
        return new User(email, passwordHash, phoneEnc, phoneHash, phoneVerifiedAt);
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email).trim().toLowerCase(Locale.ROOT);
    }

    public void markOnboardingSeen(Instant now) {
        if (onboardingSeenAt == null) {
            onboardingSeenAt = now;
        }
    }

    public boolean hasSeenOnboarding() {
        return onboardingSeenAt != null;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }

    public void changePhone(String phoneEnc, String phoneHash, Instant phoneVerifiedAt) {
        this.phoneEnc = Objects.requireNonNull(phoneEnc);
        this.phoneHash = Objects.requireNonNull(phoneHash);
        this.phoneVerifiedAt = Objects.requireNonNull(phoneVerifiedAt);
    }

    /**
     * 연속 5회 실패하면 15분 잠근다. 잠그면서 횟수는 0으로 되돌린다.
     */
    public void recordLoginFailure(Instant now) {
        failedLoginCount++;
        if (failedLoginCount >= MAX_LOGIN_FAILURES) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedLoginCount = 0;
        }
    }

    public void recordLoginSuccess() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void withdraw(Instant now) {
        if (deletedAt == null) {
            deletedAt = now;
        }
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }
}
