package com.firstory.domain.guide.controller;

import com.firstory.domain.guide.dto.GuideResponse;
import com.firstory.domain.guide.service.GuideService;
import com.firstory.global.error.ApiErrorCodes;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대화 가이드·함께 읽기", description = "로그인 필요. 확정된(COMPLETED) 동화만. 다른 회원의 동화·세션·질문은 404")
@ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.STORY_NOT_FOUND})
@RestController
@RequiredArgsConstructor
public class GuideController {

    private final GuideService guideService;

    @Operation(summary = "대화 가이드 (GDE-01)",
            description = "COMPLETED에서만. 읽기 전 의도와 팁, 쪽 사이 대화 질문(seq 순), 읽은 뒤 이어 갈 질문")
    @ApiErrorCodes(ErrorCode.INVALID_STATUS_TRANSITION)
    @GetMapping("/api/stories/{id:\\d+}/guide")
    public GuideResponse guide(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return guideService.guide(principal.getUserId(), id);
    }
}
