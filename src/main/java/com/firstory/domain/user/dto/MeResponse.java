package com.firstory.domain.user.dto;

import com.firstory.domain.user.entity.SignupSource;
import io.swagger.v3.oas.annotations.media.Schema;

public record MeResponse(
        Long id,
        String email,
        boolean onboardingSeen,
        @Schema(description = "동화 제작 소식을 이메일로 받기 (EMAIL_NOTIFY 동의)") boolean emailNotify,
        @Schema(description = "카카오로 가입한 회원") boolean kakaoLinked,
        @Schema(description = "비밀번호가 있는지. 카카오로만 가입했으면 false (비밀번호 변경 대신 비밀번호 찾기로 만든다)")
        boolean hasPassword,
        @Schema(description = "가입 경로. GENERAL(일반·기존 회원), INVITE(초대 링크), PRE_CONTACT(사전 연락), DEV(개발용)")
        SignupSource signupSource,
        @Schema(description = "동화를 이용권 없이 만들 수 있는지 (개발용 이메일)") boolean unlimitedStories,
        @Schema(description = "앞으로 AI로 만들 수 있는 동화 수 (만료되지 않은 이용권의 남은 횟수 합). 무제한이면 null. "
                + "0이면 동화 만들기 시작(POST /api/stories)이 STORY_LIMIT_REACHED") Integer storiesRemaining,
        @Schema(description = "피드백 팝업을 이미 냈는지. 종류마다 한 번이라도 저장에 성공했으면 true (팝업을 다시 띄우지 않는다)")
        FeedbackSubmitted feedbackSubmitted) {

    /**
     * @param preview 동화 확정 전 피드백 (POST /api/stories/{id}/approve의 feedback)
     * @param reading 함께 읽기 완료 피드백 (POST /api/reading-sessions/{id}/complete의 feedback)
     */
    public record FeedbackSubmitted(
            @Schema(description = "동화 확정 전 피드백을 낸 적 있음") boolean preview,
            @Schema(description = "읽기 완료 피드백을 낸 적 있음") boolean reading) {
    }
}
