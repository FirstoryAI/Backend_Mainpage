package com.firstory.domain.auth.entity;

import com.firstory.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 문자 발송 기록. 번호별 발송 횟수 제한에 쓴다. 평문 번호는 저장하지 않는다.
 */
@Getter
@Entity
@Table(name = "sms_send_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsSendLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_hash", nullable = false, length = 64)
    private String phoneHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SmsPurpose purpose;

    private SmsSendLog(String phoneHash, SmsPurpose purpose) {
        this.phoneHash = Objects.requireNonNull(phoneHash);
        this.purpose = Objects.requireNonNull(purpose);
    }

    public static SmsSendLog record(String phoneHash, SmsPurpose purpose) {
        return new SmsSendLog(phoneHash, purpose);
    }
}
