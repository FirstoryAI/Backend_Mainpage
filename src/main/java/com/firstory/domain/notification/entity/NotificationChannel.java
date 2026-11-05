package com.firstory.domain.notification.entity;

public enum NotificationChannel {
    SMS,    // V12 전에 보낸 문자 알림 기록. 지금은 보내지 않는다
    EMAIL
    // TODO(PM): WEB_PUSH는 2차 범위
}
