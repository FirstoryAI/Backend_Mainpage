package com.firstory.domain.auth.controller;

import com.firstory.domain.auth.dto.EmailCheckRequest;
import com.firstory.domain.auth.dto.EmailCheckResponse;
import com.firstory.domain.auth.dto.LoginRequest;
import com.firstory.domain.auth.dto.SendCodeRequest;
import com.firstory.domain.auth.dto.SendCodeResponse;
import com.firstory.domain.auth.dto.SignupRequest;
import com.firstory.domain.auth.dto.VerifyCodeRequest;
import com.firstory.domain.auth.service.EmailCheckService;
import com.firstory.domain.auth.service.LoginService;
import com.firstory.domain.auth.service.PhoneVerificationService;
import com.firstory.domain.auth.service.SignupService;
import com.firstory.domain.user.dto.MeResponse;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.service.MeService;
import com.firstory.global.crypto.PhoneNumber;
import com.firstory.global.security.LoginSuccessHandler;
import com.firstory.global.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import com.firstory.global.error.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.firstory.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 로그아웃은 Spring Security의 LogoutFilter가 POST /api/auth/logout에서 처리한다 (SecurityConfig).
 */
@Tag(name = "인증", description = "CSRF 토큰, 문자 인증, 가입, 로그인")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PhoneVerificationService phoneVerificationService;
    private final SignupService signupService;
    private final EmailCheckService emailCheckService;
    private final LoginService loginService;
    private final MeService meService;
    private final LoginSuccessHandler loginSuccessHandler;

    /**
     * 프론트가 앱을 처음 열 때, 로그아웃한 뒤에 부른다. 토큰을 읽어야 XSRF-TOKEN 쿠키가 내려간다.
     */
    @Operation(summary = "CSRF 토큰 받기",
            description = "XSRF-TOKEN 쿠키를 내려준다. 앱을 처음 열 때와 로그아웃한 뒤에 부른다. 응답 204")
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
        csrfToken.getToken();
    }

    @Operation(summary = "인증번호 받기",
            description = "6자리 인증번호를 문자로 보낸다. 3분 유효. 같은 번호는 60초 뒤에 다시 받을 수 있고 1시간에 5번까지. "
                    + "purpose가 SIGNUP이면 이미 가입된 번호인지 확인한다")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_PHONE_FORMAT, ErrorCode.DUPLICATE_PHONE,
            ErrorCode.SMS_RESEND_TOO_SOON, ErrorCode.SMS_RATE_LIMITED})
    @PostMapping("/phone/send-code")
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request, HttpSession session) {
        Duration validity = phoneVerificationService.sendCode(
                session, PhoneNumber.fromDisplay(request.phone()), request.purpose());
        return new SendCodeResponse(validity.toSeconds());
    }

    @Operation(summary = "인증번호 확인",
            description = "맞으면 204. 인증 결과는 세션에 10분 동안 남고 가입이나 번호 변경에 한 번 쓸 수 있다. 5번 틀리면 인증번호를 다시 받아야 한다")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_PHONE_FORMAT, ErrorCode.VERIFICATION_CODE_MISMATCH,
            ErrorCode.VERIFICATION_EXPIRED, ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED})
    @PostMapping("/phone/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyCodeRequest request, HttpSession session) {
        phoneVerificationService.verify(session, PhoneNumber.fromDisplay(request.phone()), request.code());
    }

    @Operation(summary = "이메일 중복 확인",
            description = "가입 정보 입력(AUTH-02)에서 이메일이 이미 가입됐는지 미리 본다. 대소문자는 가입과 같이 무시한다. "
                    + "IP마다 1분에 10번까지. 가입(signup)에서도 다시 확인한다")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.EMAIL_CHECK_RATE_LIMITED})
    @PostMapping("/email/check")
    public EmailCheckResponse checkEmail(@Valid @RequestBody EmailCheckRequest request,
                                         HttpServletRequest httpRequest) {
        return new EmailCheckResponse(emailCheckService.isAvailable(request.email(), httpRequest.getRemoteAddr()));
    }

    /**
     * 가입하면 바로 로그인된다 (remember-me는 발급하지 않는다).
     */
    @Operation(summary = "가입",
            description = "같은 세션에서 purpose SIGNUP으로 휴대폰 인증을 마친 뒤 부른다. 가입하면 바로 로그인된다 (로그인 상태 유지는 하지 않음). 응답 201")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_PASSWORD_FORMAT, ErrorCode.REQUIRED_CONSENT_MISSING,
            ErrorCode.INVALID_PHONE_FORMAT, ErrorCode.PHONE_NOT_VERIFIED, ErrorCode.DUPLICATE_EMAIL,
            ErrorCode.DUPLICATE_PHONE})
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse signup(@Valid @RequestBody SignupRequest request,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession();
        User user = signupService.signup(request, session);
        phoneVerificationService.clearVerified(session);
        loginSuccessHandler.login(UserPrincipal.from(user), false, httpRequest, httpResponse);
        return meService.getMe(user.getId());
    }

    @Operation(summary = "로그인",
            description = "rememberMe가 true면 90일 동안 로그인 상태를 유지한다 (FREMEMBER 쿠키). 5번 연속 틀리면 15분 동안 잠긴다. "
                    + "없는 이메일과 틀린 비밀번호는 같은 응답이다")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_CREDENTIALS, ErrorCode.ACCOUNT_LOCKED})
    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest request,
                            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UserPrincipal principal = loginService.authenticate(request.email(), request.password());
        loginSuccessHandler.login(principal, request.rememberMeRequested(), httpRequest, httpResponse);
        return meService.getMe(principal.getUserId());
    }
}
