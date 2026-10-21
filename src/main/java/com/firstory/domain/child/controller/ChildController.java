package com.firstory.domain.child.controller;

import com.firstory.domain.child.dto.ChildOptionsResponse;
import com.firstory.domain.child.dto.ChildProfileRequest;
import com.firstory.domain.child.dto.ChildResponse;
import com.firstory.domain.child.dto.DisplayNameRequest;
import com.firstory.domain.child.service.ChildService;
import com.firstory.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

/**
 * 저장된 아이 프로필 (MY-01). {id}는 숫자만 받아서 /options와 겹치지 않는다.
 */
@RestController
@RequestMapping("/api/children")
@RequiredArgsConstructor
public class ChildController {

    private static final ChildOptionsResponse OPTIONS = ChildOptionsResponse.create();

    private final ChildService childService;
    private final Clock clock;

    @GetMapping
    public List<ChildResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return childService.list(principal.getUserId());
    }

    @GetMapping("/options")
    public ChildOptionsResponse options() {
        return OPTIONS;
    }

    @GetMapping("/{id:\\d+}")
    public ChildResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return childService.get(principal.getUserId(), id);
    }

    @PostMapping
    public ResponseEntity<ChildResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                @Valid @RequestBody ChildProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(childService.create(principal.getUserId(), request.toProfile(clock)));
    }

    @PutMapping("/{id:\\d+}")
    public ChildResponse update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                @Valid @RequestBody ChildProfileRequest request) {
        return childService.update(principal.getUserId(), id, request.toProfile(clock));
    }

    @PatchMapping("/{id:\\d+}/display-name")
    public ChildResponse rename(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                @RequestBody DisplayNameRequest request) {
        return childService.rename(principal.getUserId(), id, request.displayName());
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        childService.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
