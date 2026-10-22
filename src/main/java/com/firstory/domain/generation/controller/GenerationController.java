package com.firstory.domain.generation.controller;

import com.firstory.domain.generation.dto.GenerationStatusResponse;
import com.firstory.domain.generation.service.GenerationService;
import com.firstory.global.error.ApiErrorCodes;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 생성 시작(CRT-06)과 진행 조회(GEN-01).
 */
@Tag(name = "동화 생성·검토", description = "로그인 필요. 다른 회원의 동화는 404. 차단된 동화의 변경 요청은 STORY_BLOCKED")
@ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.STORY_NOT_FOUND})
@RestController
@RequestMapping("/api/stories/{id:\\d+}")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    @Operation(summary = "동화 만들기 시작 (CRT-06)",
            description = "CONFIRMING에서만. 민감 주제가 감지됐는데(toneRequired) 분위기를 고르지 않았으면 TONE_REQUIRED. "
                    + "AI가 바로 받아들이고 뒤에서 만든다. 진행은 GET /generation으로 본다. 응답 202")
    @ApiErrorCodes({ErrorCode.TONE_REQUIRED, ErrorCode.STORY_BLOCKED, ErrorCode.INVALID_STATUS_TRANSITION,
            ErrorCode.AI_UNAVAILABLE, ErrorCode.AI_BUSY, ErrorCode.AI_STATE_MISMATCH})
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GenerationStatusResponse confirm(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return generationService.confirm(principal.getUserId(), id);
    }

    @Operation(summary = "만드는 중 진행 상태 (GEN-01)",
            description = "모든 상태에서. DB에 저장된 진행만 읽고 AI를 부르지 않아 3초마다 불러도 된다. "
                    + "stage 1 이야기 짜기, 2 글 쓰기, 3 그림 그리기, 4 책으로 묶기. status가 REVIEWING이 되면 미리보기로 간다")
    @GetMapping("/generation")
    public GenerationStatusResponse getGeneration(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long id) {
        return generationService.getGeneration(principal.getUserId(), id);
    }
}
