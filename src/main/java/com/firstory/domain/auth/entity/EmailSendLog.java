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
 * 인증 메일 발송 기록. 이메일별 발송 횟수를 센다. 이메일 원문은 남기지 않고 해시만.
 */
@Getter
@Entity
@Table(name = "email_send_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailSendLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_hash", nullable = false, length = 64)
    private String emailHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailPurpose purpose;

    private EmailSendLog(String emailHash, EmailPurpose purpose) {
        this.emailHash = Objects.requireNonNull(emailHash);
        this.purpose = Objects.requireNonNull(purpose);
    }

    public static EmailSendLog record(String emailHash, EmailPurpose purpose) {
        return new EmailSendLog(emailHash, purpose);
    }
}
