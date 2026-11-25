package com.firstory.domain.generation.entity;

/**
 * 쪽 음성 상태. 음성이 없는 쪽(이전 동화 등)은 상태 자체가 null이다.
 */
public enum AudioStatus {
    DONE,
    FAILED
}
