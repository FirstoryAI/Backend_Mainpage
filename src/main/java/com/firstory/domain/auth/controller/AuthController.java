package com.firstory.domain.auth.controller;

import com.firstory.domain.auth.dto.SendCodeRequest;
import com.firstory.domain.auth.dto.SendCodeResponse;
import com.firstory.domain.auth.dto.VerifyCodeRequest;
import com.firstory.domain.auth.service.PhoneVerificationService;
import com.firstory.global.crypto.PhoneNumber;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 로그아웃은 Spring Security의 LogoutFilter가 POST /api/auth/logout에서 처리한다 (SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PhoneVerificationService phoneVerificationService;

    /**
     * 프론트가 앱을 처음 열 때, 로그아웃한 뒤에 부른다. 토큰을 읽어야 XSRF-TOKEN 쿠키가 내려간다.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/phone/send-code")
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request, HttpSession session) {
        Duration validity = phoneVerificationService.sendCode(
                session, PhoneNumber.fromDisplay(request.phone()), request.purpose());
        return new SendCodeResponse(validity.toSeconds());
    }

    @PostMapping("/phone/verify")
    public ResponseEntity<Void> verify(@Valid @RequestBody VerifyCodeRequest request, HttpSession session) {
        phoneVerificationService.verify(session, PhoneNumber.fromDisplay(request.phone()), request.code());
        return ResponseEntity.noContent().build();
    }
}
