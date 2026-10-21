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
import com.firstory.global.error.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 변경, 휴대폰 변경, 탈퇴는 현재 비밀번호를 다시 받는다.
 */
@Tag(name = "내 정보", description = "로그인 필요")
@ApiErrorCodes(ErrorCode.UNAUTHORIZED)
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;
    private final PhoneVerificationService phoneVerificationService;
    private final SessionRevoker sessionRevoker;
    private final LoginSuccessHandler loginSuccessHandler;
    private final JsonRememberMeServices rememberMeServices;

    @Operation(summary = "내 정보", description = "휴대폰 번호는 가린 형태(010-****-5678)만 내려준다")
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return meService.getMe(principal.getUserId());
    }

    @Operation(summary = "온보딩 봤음 표시", description = "응답 204")
    @PostMapping("/onboarding-seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void onboardingSeen(@AuthenticationPrincipal UserPrincipal principal) {
        meService.markOnboardingSeen(principal.getUserId());
    }

    @Operation(summary = "문자 알림 동의 바꾸기", description = "동의하면 새 동의 기록을 남기고, 철회하면 최신 기록에 철회 시각을 남긴다. 응답 204")
    @ApiErrorCodes(ErrorCode.VALIDATION_FAILED)
    @PutMapping("/consents/sms-notify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void smsNotify(@AuthenticationPrincipal UserPrincipal principal,
                          @Valid @RequestBody SmsNotifyRequest request) {
        meService.changeSmsNotify(principal.getUserId(), request.agreed());
    }

    /**
     * 모든 기기의 세션과 remember-me 토큰을 지운다. 지금 기기만 새 세션으로 다시 로그인한다 (remember-me는 새로 발급하지 않음).
     */
    @Operation(summary = "비밀번호 변경",
            description = "현재 비밀번호를 다시 받는다. 바꾸면 모든 기기의 로그인이 끊기고 지금 기기만 다시 로그인된다. 응답 204")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.CURRENT_PASSWORD_MISMATCH, ErrorCode.INVALID_PASSWORD_FORMAT,
            ErrorCode.SAME_AS_OLD_PASSWORD})
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal UserPrincipal principal,
                               @Valid @RequestBody ChangePasswordRequest request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = meService.changePassword(
                principal.getUserId(), request.currentPassword(), request.newPassword());

        signOutEverywhere(email, httpRequest, httpResponse);
        loginSuccessHandler.login(new UserPrincipal(principal.getUserId(), email, null), false,
                httpRequest, httpResponse);
    }

    @Operation(summary = "휴대폰 번호 변경",
            description = "현재 비밀번호와, 같은 세션에서 purpose CHANGE_PHONE으로 마친 휴대폰 인증이 필요하다. 응답 204")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.CURRENT_PASSWORD_MISMATCH, ErrorCode.INVALID_PHONE_FORMAT,
            ErrorCode.PHONE_NOT_VERIFIED, ErrorCode.DUPLICATE_PHONE})
    @PostMapping("/phone")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePhone(@AuthenticationPrincipal UserPrincipal principal,
                            @Valid @RequestBody ChangePhoneRequest request, HttpSession session) {
        meService.changePhone(principal.getUserId(), request.currentPassword(), request.phone(), session);
        phoneVerificationService.clearVerified(session);
    }

    @Operation(summary = "탈퇴", description = "현재 비밀번호를 다시 받는다. 모든 기기의 로그인이 끊긴다. 응답 204")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.CURRENT_PASSWORD_MISMATCH})
    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal UserPrincipal principal,
                         @Valid @RequestBody WithdrawRequest request,
                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = meService.withdraw(principal.getUserId(), request.currentPassword());
        signOutEverywhere(email, httpRequest, httpResponse);
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
