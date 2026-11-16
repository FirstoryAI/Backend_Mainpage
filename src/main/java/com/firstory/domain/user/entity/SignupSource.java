package com.firstory.domain.user.entity;

/**
 * 가입 경로. 베타 동안 누가 어떤 경로로 들어왔는지 구분한다.
 */
public enum SignupSource {
    GENERAL,        // 일반 가입, 베타 전 기존 회원
    INVITE,         // 초대 링크
    PRE_CONTACT,    // 사전 연락 이메일
    DEV             // 개발용 무제한 이메일
}
