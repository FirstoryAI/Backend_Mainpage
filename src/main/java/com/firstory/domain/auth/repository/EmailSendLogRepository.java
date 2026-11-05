package com.firstory.domain.auth.repository;

import com.firstory.domain.auth.entity.EmailSendLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface EmailSendLogRepository extends JpaRepository<EmailSendLog, Long> {

    long countByEmailHashAndCreatedAtAfter(String emailHash, Instant after);

    boolean existsByEmailHashAndCreatedAtAfter(String emailHash, Instant after);
}
