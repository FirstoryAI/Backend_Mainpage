package com.firstory.domain.story.dto;

import com.firstory.domain.credit.service.CreditService;
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
                           List<StoryCardResponse> recent, Counts counts, Credits credits) {

    /**
     * 동화 만들기 버튼의 횟수. 베타 기간(beta)에는 횟수가 보이는 베타 홈, 아니면 원래 홈.
     */
    public record Credits(
            @Schema(description = "베타 기간인지 (서버 BETA_INVITE_ONLY). true면 횟수가 보이는 베타 홈, false면 원래 홈")
            boolean beta,
            @Schema(description = "이용권 없이 무제한인 개발용 계정. true면 remaining, total은 null") boolean unlimited,
            @Schema(description = "남은 횟수. 새 동화의 생성을 시작할 때(POST /confirm) 1 준다", example = "1")
            Integer remaining,
            @Schema(description = "지금 쓰는 무료 이용권의 전체 횟수 (초대·사전 연락 2, 일반 1). "
                    + "쓸 수 있는 이용권이 없으면 0", example = "2")
            Integer total) {

        public static Credits of(CreditService.HomeCredits credits) {
            return new Credits(credits.beta(), credits.unlimited(), credits.remaining(), credits.total());
        }
    }

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
