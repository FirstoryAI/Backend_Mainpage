package com.firstory.notification.repository;

import com.firstory.notification.domain.Notification;
import com.firstory.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByGenerationRunIdAndType(Long generationRunId, NotificationType type);
}
