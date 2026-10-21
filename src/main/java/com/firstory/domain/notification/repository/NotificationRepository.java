package com.firstory.domain.notification.repository;

import com.firstory.domain.notification.entity.Notification;
import com.firstory.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByGenerationRunIdAndType(Long generationRunId, NotificationType type);
}
