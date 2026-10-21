package com.firstory.user.domain;

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

@Getter
@Entity
@Table(name = "user_consents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 30)
    private ConsentType consentType;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "terms_version", nullable = false, length = 20)
    private String termsVersion;

    @Column(name = "agreed_at")
    private Instant agreedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    private UserConsent(User user, ConsentType consentType, boolean agreed, String termsVersion, Instant now) {
        this.user = Objects.requireNonNull(user);
        this.consentType = Objects.requireNonNull(consentType);
        this.agreed = agreed;
        this.termsVersion = Objects.requireNonNull(termsVersion);
        this.agreedAt = agreed ? now : null;
    }

    public static UserConsent record(User user, ConsentType type, boolean agreed, String termsVersion, Instant now) {
        return new UserConsent(user, type, agreed, termsVersion, now);
    }

    public void withdraw(Instant now) {
        if (consentType.isRequired()) {
            throw new IllegalStateException("필수 동의는 철회할 수 없어요: " + consentType);
        }
        if (withdrawnAt == null) {
            withdrawnAt = now;
        }
    }

    public boolean isActive() {
        return agreed && withdrawnAt == null;
    }
}
