package com.firstory.domain.story.controller;

import com.firstory.domain.story.dto.HomeResponse;
import com.firstory.domain.story.dto.LibraryResponse;
import com.firstory.domain.story.dto.StoryDetailResponse;
import com.firstory.domain.story.entity.LibraryFilter;
import com.firstory.domain.story.service.LibraryService;
import com.firstory.global.error.ApiErrorCodes;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈(HOME-01)과 서재 목록(LIB-01). 차단된(BLOCKED) 동화는 보이지 않는다.
 */
@Tag(name = "홈·서재", description = "로그인 필요")
@ApiErrorCodes(ErrorCode.UNAUTHORIZED)
@RestController
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService libraryService;

    @Operation(summary = "홈 (HOME-01)",
            description = "만드는 중·확인을 기다리는·만들지 못한 동화 최근 3개, 최근 완성 3개, 탭별 개수. 회원 정보는 /api/me")
    @GetMapping("/api/home")
    public HomeResponse home(@AuthenticationPrincipal UserPrincipal principal) {
        return libraryService.home(principal.getUserId());
    }

    @Operation(summary = "서재 목록 (LIB-01)",
            description = "filter: ALL(기본, BLOCKED 제외 전부), CREATING(만드는 중), REVIEWING(확인을 기다려요), "
                    + "UNREAD(아직 안 읽었어요), READ(함께 읽었어요), FAILED(만들지 못했어요). "
                    + "최근 수정 순. page는 0부터, size는 1~50(기본 20). 버전이 없으면 title, coverUrl은 null")
    @ApiErrorCodes(ErrorCode.VALIDATION_FAILED)
    @GetMapping("/api/stories")
    public LibraryResponse library(@AuthenticationPrincipal UserPrincipal principal,
                                   @RequestParam(defaultValue = "ALL") LibraryFilter filter,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "" + LibraryService.DEFAULT_SIZE) int size) {
        return libraryService.library(principal.getUserId(), filter, page, size);
    }

    @Operation(summary = "동화 상세 (LIB-02)",
            description = "모든 상태에서. 차단된 동화도 상태 그대로 보여준다. readCount는 끝까지 읽은 횟수, "
                    + "canRead는 COMPLETED일 때만 true")
    @ApiErrorCodes(ErrorCode.STORY_NOT_FOUND)
    @GetMapping("/api/stories/{id:\\d+}")
    public StoryDetailResponse detail(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return libraryService.detail(principal.getUserId(), id);
    }
}
