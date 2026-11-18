package com.firstory.domain.notification.service;

import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.domain.generation.repository.GenerationRunRepository;
import com.firstory.domain.notification.entity.Notification;
import com.firstory.domain.notification.entity.NotificationTemplate;
import com.firstory.domain.notification.entity.NotificationType;
import com.firstory.domain.notification.repository.NotificationRepository;
import com.firstory.domain.user.entity.User;
import com.firstory.global.mail.EmailSender;
import com.firstory.global.mail.MailContent;
import com.firstory.global.mail.MailTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 생성 실행의 이메일 알림.
 *
 * <ol>
 *   <li>회원이 탈퇴했거나 알림 수신(users.email_notify)에 동의하지 않았으면 보내지 않는다</li>
 *   <li>같은 (실행, 종류)가 이미 있으면 보내지 않는다 (UNIQUE)</li>
 *   <li>PENDING으로 저장 → 트랜잭션 밖에서 메일 발송 → SENT / FAILED 저장</li>
 * </ol>
 * 완료(STORY_COMPLETED)와 실패(STORY_FAILED)만 모든 실행에 보낸다. 시작 알림은 보내지 않는다 (#74).
 * 확정 때는 보내지 않는다 (PRD 33). 알림 실패는 생성 흐름을 깨뜨리지 않는다 (예외를 삼키고 로그만).
 * 메일에는 아이 정보, 동화 제목, 부모 입력을 넣지 않고 로그에 이메일을 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final GenerationRunRepository runRepository;
    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;
    private final MailTemplates mailTemplates;
    private final TransactionTemplate transactionTemplate;

    /**
     * 트랜잭션 밖에서 부른다 (메일 발송을 트랜잭션 안에서 하지 않는다). 예외를 던지지 않는다.
     */
    public void notify(Long runId, NotificationType type) {
        try {
            send(runId, type);
        } catch (RuntimeException e) {
            log.warn("알림을 보내지 못했어요: run_id={}, type={}, cause={}", runId, type, e.getClass().getSimpleName());
        }
    }

    private void send(Long runId, NotificationType type) {
        NotificationTemplate template = NotificationTemplate.of(type);
        Pending pending;
        try {
            pending = transactionTemplate.execute(status -> prepare(runId, type));
        } catch (DataIntegrityViolationException e) {
            // 같은 실행의 같은 알림을 다른 쪽이 먼저 저장했다
            return;
        }
        if (pending == null) {
            return;
        }
        try {
            emailSender.send(pending.email(), new MailContent(template.getSubject(),
                    MailTemplates.withContact(template.getText()),
                    mailTemplates.html(template.getHtmlTemplate(), Map.of())));
            update(pending.notificationId(), Notification::sent);
        } catch (RuntimeException e) {
            log.warn("알림 메일을 보내지 못했어요: notification_id={}, cause={}", pending.notificationId(),
                    e.getClass().getSimpleName());
            update(pending.notificationId(), notification -> notification.failed(e.getClass().getSimpleName()));
        }
    }

    private Pending prepare(Long runId, NotificationType type) {
        GenerationRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            return null;
        }
        User user = run.getStory().getUser();
        if (user.isWithdrawn() || !user.isEmailNotify()) {
            return null;
        }
        if (notificationRepository.existsByGenerationRunIdAndType(runId, type)) {
            return null;
        }
        Notification notification = notificationRepository.saveAndFlush(Notification.pending(run, type));
        return new Pending(notification.getId(), user.getEmail());
    }

    private void update(Long notificationId, Consumer<Notification> change) {
        transactionTemplate.executeWithoutResult(status -> notificationRepository.findById(notificationId)
                .ifPresent(change));
    }

    private record Pending(Long notificationId, String email) {

        @Override
        public String toString() {
            return "Pending[notificationId=" + notificationId + "]";
        }
    }
}
