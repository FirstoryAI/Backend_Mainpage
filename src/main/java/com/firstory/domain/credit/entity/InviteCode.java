package com.firstory.domain.credit.entity;

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

import java.time.Instant;
import java.util.Objects;

/**
 * 공용 초대 코드. 링크 하나를 선착순 max_uses명이 쓴다. closes_at이 지나거나 인원이 차면 동작하지 않는다.
 * 사용 인원은 가입할 때 InviteCodeRepository.claim으로 DB에서 한 번에 확인하고 올린다 (동시 가입 대비).
 * 운영에서는 서버에서 SQL로 넣는다. 코드는 비밀이라 저장소에 적지 않는다.
 */
@Getter
@Entity
@Table(name = "invite_codes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteCode extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String code;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    @Column(name = "credit_amount", nullable = false)
    private int creditAmount;

    @Column(name = "credit_expires_at")
    private Instant creditExpiresAt;

    private InviteCode(String code, int maxUses, Instant closesAt, int creditAmount, Instant creditExpiresAt) {
        this.code = Objects.requireNonNull(code);
        this.maxUses = maxUses;
        this.closesAt = Objects.requireNonNull(closesAt);
        this.creditAmount = creditAmount;
        this.creditExpiresAt = creditExpiresAt;
    }

    public static InviteCode create(String code, int maxUses, Instant closesAt, int creditAmount,
                                    Instant creditExpiresAt) {
        return new InviteCode(code, maxUses, closesAt, creditAmount, creditExpiresAt);
    }

    public boolean isOpen(Instant now) {
        return usedCount < maxUses && now.isBefore(closesAt);
    }
}
