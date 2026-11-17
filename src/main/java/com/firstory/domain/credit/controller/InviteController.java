package com.firstory.domain.credit.controller;

import com.firstory.domain.credit.service.SignupGate;
import com.firstory.global.config.FirstoryProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 베타 초대 링크. 사람들에게 https://{프론트 도메인}/api/invite/{code}를 보낸다.
 * 프론트(Vercel)가 /api를 백엔드로 넘겨서 세션 쿠키가 프론트 도메인에 남는다. 그래서 api 도메인으로 직접 보내지 않는다.
 */
@Tag(name = "1. 회원가입/로그인 인증")
@RestController
@RequestMapping("/api/invite")
@RequiredArgsConstructor
public class InviteController {

    private final SignupGate signupGate;
    private final FirstoryProperties properties;

    @Operation(summary = "베타 초대 링크",
            description = "초대 코드가 열려 있으면(선착순 인원과 마감 전) 이 브라우저 세션에 초대 표시를 남기고 "
                    + "가입 화면(firstory.beta.invite-redirect)으로 보낸다. 자리는 가입할 때 잡는다. "
                    + "없는 코드이거나 마감됐으면 표시 없이 같은 곳에 ?invite=closed를 붙여 보낸다. 응답 302")
    @GetMapping("/{code}")
    public ResponseEntity<Void> invite(@PathVariable String code, HttpServletRequest request) {
        String target = properties.beta().inviteRedirect();
        if (!signupGate.acceptInvite(code, request.getSession())) {
            target = target + (target.contains("?") ? "&" : "?") + "invite=closed";
        }
        // 상대 주소로 보낸다. 브라우저가 연 주소(프론트 도메인) 기준으로 이동한다
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target).build();
    }
}
