package com.firstory.domain.notification.service;

import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.domain.generation.entity.RunTrigger;
import com.firstory.domain.generation.repository.GenerationRunRepository;
import com.firstory.domain.notification.entity.Notification;
import com.firstory.domain.notification.entity.NotificationChannel;
import com.firstory.domain.notification.entity.NotificationTemplate;
import com.firstory.domain.notification.entity.NotificationType;
import com.firstory.domain.notification.repository.NotificationRepository;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.user.entity.ConsentType;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.entity.UserConsent;
import com.firstory.domain.user.repository.UserConsentRepository;
import com.firstory.global.crypto.PhoneCrypto;
import com.firstory.global.crypto.PhoneNumber;
import com.firstory.global.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * 생성 실행의 문자 알림.
 *
 * <ol>
 *   <li>회원의 최신 SMS_NOTIFY 동의가 활성이 아니면 보내지 않는다</li>
 *   <li>같은 (실행, 종류)가 이미 있으면 보내지 않는다 (UNIQUE)</li>
 *   <li>PENDING으로 저장 → 트랜잭션 밖에서 문자 발송 → SENT / FAILED 저장</li>
 * </ol>
 * 시작 알림(STORY_STARTED)은 처음 만들기(INITIAL)에만 보낸다. 다시 만들기·다시 시도는 완료·실패만.
 * 확정 때는 보내지 않는다 (PRD 33). 알림 실패는 생성 흐름을 깨뜨리지 않는다 (예외를 삼키고 로그만).
 * 문자에는 아이 정보, 동화 제목, 부모 입력을 넣지 않고 로그에 번호를 남기지 않는다.
 */
// TODO(PM): 다시 만들기·다시 시도의 시작 알림을 보낼지
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final GenerationRunRepository runRepository;
    private final NotificationRepository notificationRepository;
    private final UserConsentRepository consentRepository;
    private final PhoneCrypto phoneCrypto;
    private final SmsSender smsSender;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 트랜잭션 밖에서 부른다 (문자 발송을 트랜잭션 안에서 하지 않는다). 예외를 던지지 않는다.
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
            pending = transactionTemplate.execute(status -> prepare(runId, type, template));
        } catch (DataIntegrityViolationException e) {
            // 같은 실행의 같은 알림을 다른 쪽이 먼저 저장했다
            return;
        }
        if (pending == null) {
            return;
        }
        try {
            smsSender.send(pending.phone(), template.getText());
            transactionTemplate.executeWithoutResult(status -> notificationRepository.findById(pending.notificationId())
                    .ifPresent(notification -> notification.sent(null, clock.instant())));
        } catch (RuntimeException e) {
            log.warn("알림 문자를 보내지 못했어요: notification_id={}, cause={}", pending.notificationId(),
                    e.getClass().getSimpleName());
            transactionTemplate.executeWithoutResult(status -> notificationRepository.findById(pending.notificationId())
                    .ifPresent(notification -> notification.failed(e.getClass().getSimpleName())));
        }
    }

    private Pending prepare(Long runId, NotificationType type, NotificationTemplate template) {
        GenerationRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            return null;
        }
        if (type == NotificationType.STORY_STARTED && run.getTriggerType() != RunTrigger.INITIAL) {
            return null;
        }
        Story story = run.getStory();
        User user = story.getUser();
        if (user.isWithdrawn() || !agreedToSms(user)) {
            return null;
        }
        if (notificationRepository.existsByGenerationRunIdAndType(runId, type)) {
            return null;
        }
        Notification notification = notificationRepository.saveAndFlush(Notification.pending(user, story, run, type,
                NotificationChannel.SMS, template.name()));
        return new Pending(notification.getId(), phoneCrypto.decrypt(user.getPhoneEnc()));
    }

    private boolean agreedToSms(User user) {
        return consentRepository.findTopByUserIdAndConsentTypeOrderByIdDesc(user.getId(), ConsentType.SMS_NOTIFY)
                .map(UserConsent::isActive)
                .orElse(false);
    }

    private record Pending(Long notificationId, PhoneNumber phone) {
    }
}
