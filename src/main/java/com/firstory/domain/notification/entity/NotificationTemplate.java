package com.firstory.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 이메일 알림 문구. 아이 정보, 동화 제목, 부모 입력은 넣지 않는다.
 * 문구는 저장하지 않으므로(V15에서 notifications.template_code 삭제) 바꿀 때 번호를 올리지 않아도 된다.
 */
// TODO(PM): 문구 확정
@Getter
@RequiredArgsConstructor
public enum NotificationTemplate {

    STORY_STARTED_V2(NotificationType.STORY_STARTED,
            "[FIRSTORY] 맞춤 동화를 만들기 시작했어요",
            "맞춤 동화를 만들기 시작했어요. 3~4분 뒤에 FIRSTORY에서 확인해 주세요."),
    STORY_COMPLETED_V2(NotificationType.STORY_COMPLETED,
            "[FIRSTORY] 맞춤 동화가 완성됐어요",
            "맞춤 동화가 완성됐어요. FIRSTORY에서 확인하고 아이와 함께 읽어 보세요."),
    STORY_FAILED_V2(NotificationType.STORY_FAILED,
            "[FIRSTORY] 동화를 만들지 못했어요",
            "동화를 만들지 못했어요. FIRSTORY에서 다시 시도할 수 있어요.");

    private final NotificationType type;
    private final String subject;
    private final String text;

    public static NotificationTemplate of(NotificationType type) {
        return Arrays.stream(values()).filter(template -> template.type == type).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("문구가 없는 알림이에요: " + type));
    }
}
