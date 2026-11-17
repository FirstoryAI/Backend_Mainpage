package com.firstory.domain.credit.entity;

import com.firstory.domain.user.entity.SignupSource;

import java.util.Objects;

/**
 * 가입할 때 정한 가입 경로와 줄 이용권. 무료 이용권은 한 종류만 준다 (초대·사전 연락 2회면 일반 1회는 없다).
 *
 * @param inviteCode  초대 링크로 자리를 잡았을 때
 * @param preContact  사전 연락 이메일일 때
 */
public record SignupGrant(SignupSource source, InviteCode inviteCode, PreContact preContact) {

    public SignupGrant {
        Objects.requireNonNull(source);
    }

    public static SignupGrant general() {
        return new SignupGrant(SignupSource.GENERAL, null, null);
    }

    public static SignupGrant dev() {
        return new SignupGrant(SignupSource.DEV, null, null);
    }

    public static SignupGrant invite(InviteCode code) {
        return new SignupGrant(SignupSource.INVITE, Objects.requireNonNull(code), null);
    }

    public static SignupGrant preContact(PreContact contact) {
        return new SignupGrant(SignupSource.PRE_CONTACT, null, Objects.requireNonNull(contact));
    }
}
