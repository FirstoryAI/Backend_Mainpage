package com.firstory.domain.story.controller;

import com.firstory.domain.story.dto.InterviewAnswerRequest;
import com.firstory.domain.story.dto.InterviewResponse;
import com.firstory.domain.story.service.InterviewService;
import com.firstory.global.error.ApiErrorCodes;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 인터뷰 (CRT-05). 인터뷰가 끝나면(type CONFIRM) 동화는 CONFIRMING이 되고 생성 전 확인(CRT-06)으로 간다.
 */
@Tag(name = "동화 만들기", description = "로그인 필요. 다른 회원의 동화는 404. 차단된 동화의 변경 요청은 STORY_BLOCKED")
@ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.STORY_NOT_FOUND, ErrorCode.STORY_BLOCKED,
        ErrorCode.INVALID_STATUS_TRANSITION, ErrorCode.AI_UNAVAILABLE, ErrorCode.AI_BUSY,
        ErrorCode.AI_STATE_MISMATCH})
@RestController
@RequestMapping("/api/stories/{id:\\d+}/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @Operation(summary = "인터뷰 시작 (CRT-05)",
            description = "DRAFT에서만. 상황과 아이 정보가 있어야 한다. 첫 질문을 받고 INTERVIEWING이 된다")
    @ApiErrorCodes(ErrorCode.STORY_INPUT_INCOMPLETE)
    @PostMapping("/start")
    public InterviewResponse start(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return interviewService.start(principal.getUserId(), id);
    }

    @Operation(summary = "인터뷰 답변 (CRT-05)",
            description = "INTERVIEWING에서만. {answer} 또는 {skip: true} 중 하나 (answer는 500자까지). "
                    + "답변은 AI에 보내기 전에 위기 신호를 검사한다. safety.action이 ASK_REVISE면 답변을 저장하지 않고 같은 질문을 다시 주고, "
                    + "BLOCK이면 status BLOCKED, CHOOSE_TONE이면 진행하면서 toneRequired true. "
                    + "type CONFIRM이면 인터뷰가 끝나 CONFIRMING(summary 포함). AI 오류면 아무것도 저장하지 않아 같은 답변으로 다시 보낼 수 있다")
    @ApiErrorCodes(ErrorCode.VALIDATION_FAILED)
    @PostMapping("/answer")
    public InterviewResponse answer(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                    @Valid @RequestBody InterviewAnswerRequest request) {
        return interviewService.answer(principal.getUserId(), id, request.answerOrNull());
    }
}
