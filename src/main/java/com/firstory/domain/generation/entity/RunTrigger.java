package com.firstory.domain.generation.entity;

/**
 * 생성 실행 계기. 컬럼명은 trigger_type (trigger는 SQL 예약어라 피함)
 */
public enum RunTrigger {
    INITIAL,
    REGENERATE,
    RETRY
}
