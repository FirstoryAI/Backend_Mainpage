package com.firstory.domain.credit.entity;

import com.firstory.domain.user.entity.User;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * 사전 연락한 분의 이메일. 이 이메일로 가입하면 초대 링크 없이 가입하고 무료 이용권을 받는다.
 * 이미 회원이면 다음에 로그인해 쓸 때 받는다 (CreditService.syncPreContact). 이용권은 한 번만 준다.
 */
@Getter
@Entity
@Table(name = "pre_contacts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreContact extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "credit_amount", nullable = false)
    private int creditAmount;

    @Column(name = "credit_expires_at")
    private Instant creditExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_user_id")
    private User grantedUser;

    @Column(name = "granted_at")
    private Instant grantedAt;

    private PreContact(String email, int creditAmount, Instant creditExpiresAt) {
        this.email = User.normalizeEmail(email);
        this.creditAmount = creditAmount;
        this.creditExpiresAt = creditExpiresAt;
    }

    public static PreContact register(String email, int creditAmount, Instant creditExpiresAt) {
        return new PreContact(email, creditAmount, creditExpiresAt);
    }

    public boolean isGranted() {
        return grantedAt != null;
    }

    public void markGranted(User user, Instant now) {
        this.grantedUser = Objects.requireNonNull(user);
        this.grantedAt = Objects.requireNonNull(now);
    }
}
