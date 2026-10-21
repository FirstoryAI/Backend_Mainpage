package com.firstory.domain.auth.repository;

import com.firstory.domain.auth.entity.SmsSendLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface SmsSendLogRepository extends JpaRepository<SmsSendLog, Long> {

    long countByPhoneHashAndCreatedAtAfter(String phoneHash, Instant after);

    boolean existsByPhoneHashAndCreatedAtAfter(String phoneHash, Instant after);
}
