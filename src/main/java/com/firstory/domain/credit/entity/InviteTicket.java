package com.firstory.domain.credit.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 세션에 넣는 "초대 링크로 들어왔다" 표시. 초대 링크(GET /api/invite/{code})에서 남기고 가입에서 한 번 쓴다.
 * 자리는 가입할 때 잡는다 (링크만 누른 사람은 인원에 세지 않는다).
 */
public record InviteTicket(Long inviteCodeId, Instant issuedAt) implements Serializable {

    public InviteTicket {
        Objects.requireNonNull(inviteCodeId);
        Objects.requireNonNull(issuedAt);
    }
}
