package com.firstory.domain.story.dto;

import com.firstory.domain.story.entity.SituationCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * 홈 (HOME-01). 회원 정보(이메일, 휴대폰)는 넣지 않는다 (/api/me가 따로 있다).
 *
 * @param inProgress 만드는 중·확인을 기다리는·만들지 못한 동화, 최근 수정 순 3개
 * @param recent     완성된 동화, 최근 확정 순 3개
 */
public record HomeResponse(boolean onboardingSeen, List<StoryCardResponse> inProgress,
                           List<StoryCardResponse> recent, Counts counts) {

    /**
     * 서재 탭별 개수 (LibraryFilter 기준). BLOCKED와 임시저장(DRAFT, INTERVIEWING, CONFIRMING)은 세지 않는다.
     */
    public record Counts(long creating, long reviewing, long unread, long read, long failed,
                         @Schema(description = "상황 분류별 개수 (서재 주제 칩). 분류 8개가 모두 있고 없으면 0. "
                                 + "BLOCKED와 임시저장은 세지 않는다 (filter=ALL과 같은 기준)",
                                 example = "{\"FRIEND_CONFLICT\": 2, \"NEW_ENVIRONMENT\": 1, \"SEPARATION\": 0, "
                                         + "\"SAFETY\": 0, \"RULES\": 1, \"FAMILY_CHANGE\": 0, \"FEAR\": 0, "
                                         + "\"OTHER\": 1}")
                         Map<SituationCategory, Long> byCategory) {
    }
}
