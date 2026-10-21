package com.firstory.domain.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그아웃은 Spring Security의 LogoutFilter가 POST /api/auth/logout에서 처리한다 (SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * 프론트가 앱을 처음 열 때, 로그아웃한 뒤에 부른다. 토큰을 읽어야 XSRF-TOKEN 쿠키가 내려간다.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }
}
