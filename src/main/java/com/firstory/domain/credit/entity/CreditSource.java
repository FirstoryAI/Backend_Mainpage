package com.firstory.domain.credit.entity;

/**
 * 이용권을 받은 경로.
 */
public enum CreditSource {
    SIGNUP_FREE,    // 일반 가입·기존 회원 무료 1회
    INVITE,         // 초대 링크 무료
    PRE_CONTACT     // 사전 연락 이메일 무료
    // TODO(PM): 한달권(SUBSCRIPTION, 4회, 1개월). 결제를 만들 때 이용권 한 장으로 넣는다
}
