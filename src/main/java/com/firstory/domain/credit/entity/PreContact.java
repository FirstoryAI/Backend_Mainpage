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

    // 이용권을 받을 수 있는 마감. 지나면 사전 연락이 아니라 일반 가입·일반 회원으로 본다. NULL이면 마감 없음
    @Column(name = "closes_at")
    private Instant closesAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_user_id")
    private User grantedUser;

    @Column(name = "granted_at")
    private Instant grantedAt;

    private PreContact(String email, int creditAmount, Instant closesAt, Instant creditExpiresAt) {
        this.email = User.normalizeEmail(email);
        this.creditAmount = creditAmount;
        this.closesAt = closesAt;
        this.creditExpiresAt = creditExpiresAt;
    }

    public static PreContact register(String email, int creditAmount, Instant closesAt, Instant creditExpiresAt) {
        return new PreContact(email, creditAmount, closesAt, creditExpiresAt);
    }

    public boolean isGranted() {
        return grantedAt != null;
    }

    /**
     * 아직 이용권을 받지 않았고 받을 수 있는 마감(closes_at)과 이용권 기한(credit_expires_at) 전이면 사전 연락으로 받는다.
     * 마감이 지난 뒤에는 사전 연락이 아니라 일반 가입·일반 회원으로 본다. 이미 받은 이용권은 credit_expires_at까지 쓴다.
     */
    public boolean isAvailable(Instant now) {
        return !isGranted() && before(closesAt, now) && before(creditExpiresAt, now);
    }

    private static boolean before(Instant deadline, Instant now) {
        return deadline == null || deadline.isAfter(now);
    }

    public void markGranted(User user, Instant now) {
        this.grantedUser = Objects.requireNonNull(user);
        this.grantedAt = Objects.requireNonNull(now);
    }
}
