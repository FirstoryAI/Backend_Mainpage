package com.firstory.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 문자 알림 문구. 아이 정보, 동화 제목, 부모 입력은 넣지 않는다.
 * code는 notifications.template_code에 남는다. 문구를 바꾸면 code 뒤 번호를 올린다.
 */
// TODO(PM): 문구 확정
@Getter
@RequiredArgsConstructor
public enum NotificationTemplate {

    STORY_STARTED_V1(NotificationType.STORY_STARTED,
            "[FIRSTORY] 맞춤 동화를 만들기 시작했어요. 3~4분 뒤에 확인해 주세요."),
    STORY_COMPLETED_V1(NotificationType.STORY_COMPLETED,
            "[FIRSTORY] 맞춤 동화가 완성됐어요. 확인하고 아이와 함께 읽어 보세요."),
    STORY_FAILED_V1(NotificationType.STORY_FAILED,
            "[FIRSTORY] 동화를 만들지 못했어요. 사이트에서 다시 시도할 수 있어요.");

    private final NotificationType type;
    private final String text;

    public static NotificationTemplate of(NotificationType type) {
        return Arrays.stream(values()).filter(template -> template.type == type).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("문구가 없는 알림이에요: " + type));
    }
}
