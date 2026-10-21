package com.firstory.domain.child.controller;

import com.firstory.domain.child.dto.ChildOptionsResponse;
import com.firstory.domain.child.dto.ChildProfileRequest;
import com.firstory.domain.child.dto.ChildResponse;
import com.firstory.domain.child.dto.DisplayNameRequest;
import com.firstory.domain.child.service.ChildService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

/**
 * 저장된 아이 프로필 (MY-01). {id}는 숫자만 받아서 /options와 겹치지 않는다.
 */
@Tag(name = "아이 프로필", description = "저장된 아이 프로필. 로그인 필요. 다른 회원의 아이는 404")
@ApiErrorCodes(ErrorCode.UNAUTHORIZED)
@RestController
@RequestMapping("/api/children")
@RequiredArgsConstructor
public class ChildController {

    private static final ChildOptionsResponse OPTIONS = ChildOptionsResponse.create();

    private final ChildService childService;
    private final Clock clock;

    @Operation(summary = "아이 목록", description = "id 오름차순")
    @GetMapping
    public List<ChildResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return childService.list(principal.getUserId());
    }

    @Operation(summary = "입력 선택지", description = "성향, 성별 버튼 문구와 좋아하는 것 개수·길이 제한")
    @GetMapping("/options")
    public ChildOptionsResponse options() {
        return OPTIONS;
    }

    @Operation(summary = "아이 상세")
    @ApiErrorCodes(ErrorCode.CHILD_NOT_FOUND)
    @GetMapping("/{id:\\d+}")
    public ChildResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return childService.get(principal.getUserId(), id);
    }

    @Operation(summary = "아이 추가",
            description = "호칭은 비어 있는 가장 앞 글자로 자동으로 붙는다 (아이 A, 아이 B…). traits, interests는 생략하면 빈 목록. 응답 201")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_BIRTH_YM, ErrorCode.INTERESTS_LIMIT_EXCEEDED,
            ErrorCode.INTEREST_TOO_LONG, ErrorCode.CHILD_LABEL_EXHAUSTED})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChildResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                @Valid @RequestBody ChildProfileRequest request) {
        return childService.create(principal.getUserId(), request.toProfile(clock));
    }

    @Operation(summary = "아이 정보 수정", description = "생년월, 성별, 성향, 좋아하는 것을 바꾼다. 호칭은 바꾸지 않는다")
    @ApiErrorCodes({ErrorCode.CHILD_NOT_FOUND, ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_BIRTH_YM,
            ErrorCode.INTERESTS_LIMIT_EXCEEDED, ErrorCode.INTEREST_TOO_LONG})
    @PutMapping("/{id:\\d+}")
    public ChildResponse update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                @Valid @RequestBody ChildProfileRequest request) {
        return childService.update(principal.getUserId(), id, request.toProfile(clock));
    }

    @Operation(summary = "호칭 변경", description = "1~30자. 호칭은 부모에게만 보이고 AI로 보내지 않는다")
    @ApiErrorCodes({ErrorCode.CHILD_NOT_FOUND, ErrorCode.INVALID_CHILD_NAME})
    @PatchMapping("/{id:\\d+}/display-name")
    public ChildResponse rename(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                @RequestBody DisplayNameRequest request) {
        return childService.rename(principal.getUserId(), id, request.displayName());
    }

    @Operation(summary = "아이 프로필 삭제", description = "이 아이로 만든 동화는 그대로 남고 연결만 끊긴다. 응답 204")
    @ApiErrorCodes(ErrorCode.CHILD_NOT_FOUND)
    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        childService.delete(principal.getUserId(), id);
    }
}
