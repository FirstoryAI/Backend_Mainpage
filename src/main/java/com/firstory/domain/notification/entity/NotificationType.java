package com.firstory.domain.notification.entity;

// TODO(PM): 알림 문구는 PM 버전으로 확정 예정
public enum NotificationType {
    /**
     * 더 이상 보내지 않는다 (#74). 지난 알림 기록(notifications.type)을 읽을 수 있게 남겨 둔다.
     */
    @Deprecated
    STORY_STARTED,
    STORY_COMPLETED,
    STORY_FAILED
}
