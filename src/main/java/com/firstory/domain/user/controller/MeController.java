package com.firstory.domain.user.controller;

import com.firstory.domain.auth.service.PhoneVerificationService;
import com.firstory.domain.user.dto.ChangePasswordRequest;
import com.firstory.domain.user.dto.ChangePhoneRequest;
import com.firstory.domain.user.dto.MeResponse;
import com.firstory.domain.user.dto.SmsNotifyRequest;
import com.firstory.domain.user.dto.WithdrawRequest;
import com.firstory.domain.user.service.MeService;
import com.firstory.global.security.JsonRememberMeServices;
import com.firstory.global.security.LoginSuccessHandler;
import com.firstory.global.security.SessionRevoker;
import com.firstory.global.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 변경, 휴대폰 변경, 탈퇴는 현재 비밀번호를 다시 받는다.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;
    private final PhoneVerificationService phoneVerificationService;
    private final SessionRevoker sessionRevoker;
    private final LoginSuccessHandler loginSuccessHandler;
    private final JsonRememberMeServices rememberMeServices;

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return meService.getMe(principal.getUserId());
    }

    @PostMapping("/onboarding-seen")
    public ResponseEntity<Void> onboardingSeen(@AuthenticationPrincipal UserPrincipal principal) {
        meService.markOnboardingSeen(principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/consents/sms-notify")
    public ResponseEntity<Void> smsNotify(@AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody SmsNotifyRequest request) {
        meService.changeSmsNotify(principal.getUserId(), request.agreed());
        return ResponseEntity.noContent().build();
    }

    /**
     * 모든 기기의 세션과 remember-me 토큰을 지운다. 지금 기기만 새 세션으로 다시 로그인한다 (remember-me는 새로 발급하지 않음).
     */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody ChangePasswordRequest request,
                                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = meService.changePassword(
                principal.getUserId(), request.currentPassword(), request.newPassword());

        signOutEverywhere(email, httpRequest, httpResponse);
        loginSuccessHandler.login(new UserPrincipal(principal.getUserId(), email, null), false,
                httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/phone")
    public ResponseEntity<Void> changePhone(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody ChangePhoneRequest request, HttpSession session) {
        meService.changePhone(principal.getUserId(), request.currentPassword(), request.phone(), session);
        phoneVerificationService.clearVerified(session);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal UserPrincipal principal,
                                         @Valid @RequestBody WithdrawRequest request,
                                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = meService.withdraw(principal.getUserId(), request.currentPassword());
        signOutEverywhere(email, httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    // 세션을 무효화하면 Spring Session이 FSESSION 쿠키를 지운다
    private void signOutEverywhere(String email, HttpServletRequest request, HttpServletResponse response) {
        sessionRevoker.revokeAll(email);
        HttpSession current = request.getSession(false);
        if (current != null) {
            current.invalidate();
        }
        SecurityContextHolder.clearContext();
        rememberMeServices.clearCookie(request, response);
    }
}
