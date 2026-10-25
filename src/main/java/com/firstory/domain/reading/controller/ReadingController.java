package com.firstory.domain.reading.controller;

import com.firstory.domain.reading.dto.BookResponse;
import com.firstory.domain.reading.dto.DialogueRequest;
import com.firstory.domain.reading.dto.ProgressRequest;
import com.firstory.domain.reading.dto.ReadingSessionResponse;
import com.firstory.domain.reading.service.ReadingService;
import com.firstory.global.error.ApiErrorCodes;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 함께 읽기 (RD-01~03). 확정된(COMPLETED) 동화만. 세션 id로 들어오는 API는 세션 → 동화 → 회원까지 확인한다.
 */
@Tag(name = "대화 가이드·함께 읽기", description = "로그인 필요. 확정된(COMPLETED) 동화만. 다른 회원의 동화·세션·질문은 404")
@ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.INVALID_STATUS_TRANSITION})
@RestController
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingService readingService;

    @Operation(summary = "읽기 화면 내용 (RD-01)", description = "확정된 버전의 표지, 쪽, 쪽 사이 대화 질문(afterPage)")
    @ApiErrorCodes(ErrorCode.STORY_NOT_FOUND)
    @GetMapping("/api/stories/{id:\\d+}/book")
    public BookResponse book(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return readingService.book(principal.getUserId(), id);
    }

    @Operation(summary = "읽기 시작", description = "새 읽기 세션을 만든다. lastPage 0(표지)에서 시작. 응답 201")
    @ApiErrorCodes(ErrorCode.STORY_NOT_FOUND)
    @PostMapping("/api/stories/{id:\\d+}/reading-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingSessionResponse start(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return readingService.start(principal.getUserId(), id);
    }

    @Operation(summary = "이어 읽기", description = "가장 최근의 끝나지 않은 세션과 마지막 쪽. 없으면 204")
    @ApiResponse(responseCode = "204", description = "이어 읽을 세션이 없어요")
    @ApiErrorCodes(ErrorCode.STORY_NOT_FOUND)
    @GetMapping("/api/stories/{id:\\d+}/reading-sessions/resume")
    public ResponseEntity<ReadingSessionResponse> resume(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable Long id) {
        return readingService.resume(principal.getUserId(), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "쪽 넘김", description = "page는 1~10. 끝난 세션은 INVALID_STATUS_TRANSITION. 응답 204")
    @ApiErrorCodes({ErrorCode.READING_SESSION_NOT_FOUND, ErrorCode.PAGE_OUT_OF_RANGE, ErrorCode.VALIDATION_FAILED})
    @PutMapping("/api/reading-sessions/{sessionId:\\d+}/progress")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void progress(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long sessionId,
                         @Valid @RequestBody ProgressRequest request) {
        readingService.moveTo(principal.getUserId(), sessionId, request.page());
    }

    @Operation(summary = "대화 버튼 (RD-02)",
            description = "TALKED(이야기 나눴어요) 또는 SKIPPED(건너뛰기). 같은 질문에 다시 누르면 마지막 값으로 바꾼다. "
                    + "이 세션 버전의 질문이 아니면 404. 응답 204")
    @ApiErrorCodes({ErrorCode.READING_SESSION_NOT_FOUND, ErrorCode.PROMPT_NOT_FOUND, ErrorCode.VALIDATION_FAILED})
    @PutMapping("/api/reading-sessions/{sessionId:\\d+}/dialogue/{promptId:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dialogue(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long sessionId,
                         @PathVariable Long promptId, @Valid @RequestBody DialogueRequest request) {
        readingService.recordDialogue(principal.getUserId(), sessionId, promptId, request.action());
    }

    @Operation(summary = "다 읽음 (RD-03)", description = "세션을 끝내고 동화를 \"함께 읽었어요\"로 바꾼다. 두 번 불러도 204")
    @ApiErrorCodes(ErrorCode.READING_SESSION_NOT_FOUND)
    @PostMapping("/api/reading-sessions/{sessionId:\\d+}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long sessionId) {
        readingService.complete(principal.getUserId(), sessionId);
    }
}
