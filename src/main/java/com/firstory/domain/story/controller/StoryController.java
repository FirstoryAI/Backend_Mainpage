package com.firstory.domain.story.controller;

import com.firstory.domain.story.dto.MessageRequest;
import com.firstory.domain.story.dto.SituationRequest;
import com.firstory.domain.story.dto.StoryChildRequest;
import com.firstory.domain.story.dto.StoryCreatedResponse;
import com.firstory.domain.story.dto.StoryDraftResponse;
import com.firstory.domain.story.dto.StoryStepResponse;
import com.firstory.domain.story.dto.SummaryRequest;
import com.firstory.domain.story.dto.ToneRequest;
import com.firstory.domain.story.service.StoryDraftService;
import com.firstory.global.error.ApiErrorCodes;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 동화 만들기 입력 (CRT-01~04, ST-04). 다른 회원의 동화는 404.
 * 글을 받는 단계는 위기 신호를 검사한다. 응답의 saved가 false면 입력을 저장하지 않은 것이다.
 */
@Tag(name = "동화 만들기", description = "로그인 필요. 다른 회원의 동화는 404. 차단된 동화의 변경 요청은 STORY_BLOCKED")
@ApiErrorCodes(ErrorCode.UNAUTHORIZED)
@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {

    private static final String STEP_RESPONSE = " 위기 신호에 따라 safety.action이 PROCEED(진행), CHOOSE_TONE(저장하고 ST-04 분위기 선택), "
            + "ASK_REVISE(저장하지 않음, safety.reason을 보여주고 다시 쓰기), BLOCK(저장하지 않음, status BLOCKED, ST-05와 helpKind)";

    private final StoryDraftService storyDraftService;

    @Operation(summary = "새 동화 만들기", description = "DRAFT 상태의 빈 동화를 만든다. 응답 201")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoryCreatedResponse create(@AuthenticationPrincipal UserPrincipal principal) {
        return storyDraftService.create(principal.getUserId());
    }

    @Operation(summary = "상황 입력 (CRT-01, 02)",
            description = "DRAFT, CONFIRMING(생성 전 확인에서 고치기)에서. 형식 오류는 위기 신호 검사 전에 400. 검사 대상: description, customCategory, customReaction."
                    + STEP_RESPONSE)
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.DESCRIPTION_TOO_LONG, ErrorCode.CUSTOM_VALUE_REQUIRED,
            ErrorCode.CUSTOM_VALUE_TOO_LONG, ErrorCode.MOMENT_ONLY_FOR_PAST, ErrorCode.STORY_NOT_FOUND,
            ErrorCode.STORY_BLOCKED, ErrorCode.INVALID_STATUS_TRANSITION, ErrorCode.AI_UNAVAILABLE})
    @PutMapping("/{id:\\d+}/situation")
    public StoryStepResponse writeSituation(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                            @Valid @RequestBody SituationRequest request) {
        return storyDraftService.writeSituation(principal.getUserId(), id, request);
    }

    @Operation(summary = "아이 정보 입력 (CRT-03)",
            description = "DRAFT, CONFIRMING(생성 전 확인에서 고치기)에서. 저장된 프로필을 불러왔으면 childId, saveForNextTime이면 프로필을 저장하거나 갱신한다. "
                    + "검사 대상: customTrait, customInterests." + STEP_RESPONSE)
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_BIRTH_YM, ErrorCode.INTERESTS_LIMIT_EXCEEDED,
            ErrorCode.CUSTOM_VALUE_REQUIRED, ErrorCode.CUSTOM_VALUE_TOO_LONG, ErrorCode.CHILD_LABEL_EXHAUSTED,
            ErrorCode.STORY_NOT_FOUND, ErrorCode.CHILD_NOT_FOUND, ErrorCode.STORY_BLOCKED,
            ErrorCode.INVALID_STATUS_TRANSITION, ErrorCode.AI_UNAVAILABLE})
    @PutMapping("/{id:\\d+}/child")
    public StoryStepResponse writeChild(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                        @Valid @RequestBody StoryChildRequest request) {
        return storyDraftService.writeChild(principal.getUserId(), id, request);
    }

    @Operation(summary = "전하고 싶은 말 입력 (CRT-04)",
            description = "DRAFT, CONFIRMING(생성 전 확인에서 고치기)에서. message가 null이거나 비어 있으면 \"아직 잘 모르겠어요\". "
                    + "인터뷰가 채운 다른 값은 그대로 둔다. 검사 대상: message." + STEP_RESPONSE)
    @ApiErrorCodes({ErrorCode.STORY_NOT_FOUND, ErrorCode.STORY_BLOCKED, ErrorCode.INVALID_STATUS_TRANSITION,
            ErrorCode.AI_UNAVAILABLE})
    @PutMapping("/{id:\\d+}/message")
    public StoryStepResponse writeMessage(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                          @RequestBody MessageRequest request) {
        return storyDraftService.writeMessage(principal.getUserId(), id, request);
    }

    @Operation(summary = "이번 이야기에서 나누고 싶은 것 고치기 (CRT-06)",
            description = "CONFIRMING에서만. 인터뷰가 만든 summary를 부모가 고친다. 1~300자. 만들기를 누르면 AI에 함께 간다. "
                    + "검사 대상: summary." + STEP_RESPONSE)
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.STORY_NOT_FOUND, ErrorCode.STORY_BLOCKED,
            ErrorCode.INVALID_STATUS_TRANSITION, ErrorCode.AI_UNAVAILABLE})
    @PutMapping("/{id:\\d+}/summary")
    public StoryStepResponse editSummary(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                         @Valid @RequestBody SummaryRequest request) {
        return storyDraftService.editSummary(principal.getUserId(), id, request);
    }

    @Operation(summary = "이야기 분위기 선택 (ST-04)",
            description = "DRAFT, INTERVIEWING, CONFIRMING에서. 위기 신호를 검사하지 않아 safety는 null")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.STORY_NOT_FOUND, ErrorCode.STORY_BLOCKED,
            ErrorCode.INVALID_STATUS_TRANSITION})
    @PutMapping("/{id:\\d+}/tone")
    public StoryStepResponse chooseTone(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                        @Valid @RequestBody ToneRequest request) {
        return storyDraftService.chooseTone(principal.getUserId(), id, request.tone());
    }

    @Operation(summary = "만들던 동화 조회 (이어서 만들기, CRT-06)",
            description = "모든 상태에서. 지금까지의 입력, 인터뷰 질문·답변, 요약, 분위기")
    @ApiErrorCodes(ErrorCode.STORY_NOT_FOUND)
    @GetMapping("/{id:\\d+}/draft")
    public StoryDraftResponse getDraft(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return storyDraftService.getDraft(principal.getUserId(), id);
    }

    @Operation(summary = "동화 지우기 (나가기, LIB-02)",
            description = "생성 중(REQUESTED, GENERATING_TEXT, GENERATING_IMAGES)을 뺀 모든 상태에서. "
                    + "지운 뒤에는 목록·상세·그림 API에서 모두 404. 응답 204")
    @ApiErrorCodes({ErrorCode.STORY_NOT_FOUND, ErrorCode.INVALID_STATUS_TRANSITION})
    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        storyDraftService.delete(principal.getUserId(), id);
    }
}
