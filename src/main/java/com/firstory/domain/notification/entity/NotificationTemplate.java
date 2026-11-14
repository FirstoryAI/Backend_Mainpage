package com.firstory.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.firstory.global.mail.MailTemplates;

import java.util.Arrays;

/**
 * 이메일 알림 문구. 아이 정보, 동화 제목, 부모 입력은 넣지 않는다.
 * 문구는 저장하지 않으므로(V15에서 notifications.template_code 삭제) 바꿀 때 번호를 올리지 않아도 된다.
 * HTML은 htmlTemplate(resources/mail/*.html), 글자 본문은 text (HTML을 못 여는 메일 앱용).
 */
@Getter
@RequiredArgsConstructor
public enum NotificationTemplate {

    STORY_STARTED(NotificationType.STORY_STARTED, MailTemplates.STORY_STARTED,
            "[FIRSTORY] 맞춤 동화를 만들기 시작했어요",
            "동화를 만들기 시작했어요.\n보통 4~5분이면 완성돼요. 다 되면 다시 알려 드릴게요."),
    STORY_COMPLETED(NotificationType.STORY_COMPLETED, MailTemplates.STORY_COMPLETED,
            "[FIRSTORY] 맞춤 동화가 완성됐어요",
            "맞춤 동화가 완성됐어요.\nFIRSTORY 서재에서 새 동화를 확인해 보세요."),
    STORY_FAILED(NotificationType.STORY_FAILED, MailTemplates.STORY_FAILED,
            "[FIRSTORY] 동화를 끝까지 만들지 못했어요",
            "동화를 끝까지 만들지 못했어요.\n입력하신 내용은 그대로 있어요. FIRSTORY 서재에서 다시 시도할 수 있어요.");

    private final NotificationType type;
    private final String htmlTemplate;
    private final String subject;
    private final String text;

    public static NotificationTemplate of(NotificationType type) {
        return Arrays.stream(values()).filter(template -> template.type == type).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("문구가 없는 알림이에요: " + type));
    }
}
