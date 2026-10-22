package com.firstory.domain.story.dto;

import java.util.List;

/**
 * 홈 (HOME-01). 회원 정보(이메일, 휴대폰)는 넣지 않는다 (/api/me가 따로 있다).
 *
 * @param inProgress 만드는 중·확인을 기다리는·만들지 못한 동화, 최근 수정 순 3개
 * @param recent     완성된 동화, 최근 확정 순 3개
 */
public record HomeResponse(boolean onboardingSeen, List<StoryCardResponse> inProgress,
                           List<StoryCardResponse> recent, Counts counts) {

    /**
     * 서재 탭별 개수 (BLOCKED는 세지 않는다).
     */
    public record Counts(long creating, long reviewing, long unread, long read, long failed) {
    }
}
