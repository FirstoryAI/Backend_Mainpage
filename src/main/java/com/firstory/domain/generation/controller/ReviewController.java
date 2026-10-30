package com.firstory.domain.generation.controller;

import com.firstory.domain.generation.dto.ApproveResponse;
import com.firstory.domain.generation.dto.CurrentVersionRequest;
import com.firstory.domain.generation.dto.GenerationStatusResponse;
import com.firstory.domain.generation.dto.PreviewResponse;
import com.firstory.domain.generation.dto.RegenerateRequest;
import com.firstory.domain.generation.dto.VersionResponse;
import com.firstory.domain.generation.service.ReviewService;
import com.firstory.domain.story.dto.StoryStepResponse;
import com.firstory.global.error.ApiErrorCodes;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 부모 검토 (REV-01~04)와 실패 후 다시 시도 (ST-03).
 */
@Tag(name = "동화 생성·검토", description = "로그인 필요. 다른 회원의 동화는 404. 차단된 동화의 변경 요청은 STORY_BLOCKED")
@ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.STORY_NOT_FOUND})
@RestController
@RequestMapping("/api/stories/{id:\\d+}")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "미리보기 (REV-01)",
            description = "REVIEWING, COMPLETED에서. 지금 보여 주는 버전의 10쪽. 그림은 imageUrl(그림 API)로 받는다. "
                    + "그림이 실패한 쪽은 imageStatus FAILED, imageUrl null")
    @ApiErrorCodes({ErrorCode.STORY_BLOCKED, ErrorCode.INVALID_STATUS_TRANSITION, ErrorCode.VERSION_NOT_FOUND})
    @GetMapping("/preview")
    public PreviewResponse preview(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return reviewService.preview(principal.getUserId(), id);
    }

    @Operation(summary = "다시 만들기 (REV-02)",
            description = "REVIEWING에서. scope는 CHARACTER_STORY(이야기·인물), TEXT(글), IMAGES(그림만). "
                    + "IMAGES는 pages로 다시 그릴 쪽을 고른다(비우면 전체). 메모(feedback, 300자)는 위기 신호를 먼저 검사해 "
                    + "ASK_REVISE면 다시 만들지 않고 횟수도 그대로(saved false), BLOCK이면 차단. "
                    + "시작하면 status REQUESTED, 진행은 GET /generation으로 본다. 응답 202")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_PAGE_SELECTION, ErrorCode.FEEDBACK_TOO_LONG,
            ErrorCode.REGENERATION_LIMIT_REACHED, ErrorCode.STORY_BLOCKED, ErrorCode.INVALID_STATUS_TRANSITION,
            ErrorCode.VERSION_NOT_FOUND, ErrorCode.AI_UNAVAILABLE, ErrorCode.AI_BUSY, ErrorCode.AI_STATE_MISMATCH})
    @PostMapping("/regenerate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StoryStepResponse regenerate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                        @Valid @RequestBody RegenerateRequest request) {
        return reviewService.regenerate(principal.getUserId(), id, request);
    }

    @Operation(summary = "버전 목록",
            description = "모든 상태에서. versionNo 오름차순. parentVersionNo로 어느 버전에서 다시 만들었는지 알 수 있다")
    @GetMapping("/versions")
    public List<VersionResponse> versions(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return reviewService.versions(principal.getUserId(), id);
    }

    @Operation(summary = "보여 줄 버전 바꾸기 (되돌리기, 앞으로 가기)",
            description = "REVIEWING에서. 다시 만들기 횟수는 바뀌지 않는다. 응답은 바꾼 버전의 미리보기")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.VERSION_NOT_FOUND, ErrorCode.STORY_BLOCKED,
            ErrorCode.INVALID_STATUS_TRANSITION})
    @PutMapping("/current-version")
    public PreviewResponse showVersion(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                       @Valid @RequestBody CurrentVersionRequest request) {
        return reviewService.showVersion(principal.getUserId(), id, request.versionId());
    }

    @Operation(summary = "확정 (REV-04)", description = "REVIEWING에서. 지금 보여 주는 버전으로 확정한다. 알림은 보내지 않는다")
    @ApiErrorCodes({ErrorCode.STORY_BLOCKED, ErrorCode.INVALID_STATUS_TRANSITION})
    @PostMapping("/approve")
    public ApproveResponse approve(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return reviewService.approve(principal.getUserId(), id);
    }

    @Operation(summary = "실패 후 다시 시도 (ST-03)",
            description = "FAILED에서. 마지막 실패와 같은 범위로 다시 만든다. 다시 만들기 횟수에 넣지 않는다. 응답 202")
    @ApiErrorCodes({ErrorCode.STORY_BLOCKED, ErrorCode.INVALID_STATUS_TRANSITION, ErrorCode.AI_UNAVAILABLE,
            ErrorCode.AI_BUSY, ErrorCode.AI_STATE_MISMATCH})
    @PostMapping("/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GenerationStatusResponse retry(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return reviewService.retry(principal.getUserId(), id);
    }
}
