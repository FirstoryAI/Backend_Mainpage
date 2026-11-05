package com.firstory.domain.auth.controller;

import com.firstory.domain.auth.dto.EmailCheckRequest;
import com.firstory.domain.auth.dto.EmailCheckResponse;
import com.firstory.domain.auth.dto.KakaoLoginRequest;
import com.firstory.domain.auth.dto.KakaoLoginResponse;
import com.firstory.domain.auth.dto.KakaoSignupRequest;
import com.firstory.domain.auth.dto.LoginRequest;
import com.firstory.domain.auth.dto.PasswordResetRequest;
import com.firstory.domain.auth.dto.SendCodeRequest;
import com.firstory.domain.auth.dto.SendCodeResponse;
import com.firstory.domain.auth.dto.SignupRequest;
import com.firstory.domain.auth.dto.VerifyCodeRequest;
import com.firstory.domain.auth.entity.PendingKakaoSignup;
import com.firstory.domain.auth.service.EmailCheckService;
import com.firstory.domain.auth.service.EmailVerificationService;
import com.firstory.domain.auth.service.KakaoLoginService;
import com.firstory.domain.auth.service.LoginService;
import com.firstory.domain.auth.service.PasswordResetService;
import com.firstory.domain.auth.service.SignupService;
import com.firstory.domain.user.dto.MeResponse;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.service.MeService;
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
import java.util.Optional;


@Tag(name = "1. 회원가입/로그인 인증", description = "CSRF 토큰, 이메일 인증, 가입, 로그인, 카카오 로그인")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EmailVerificationService emailVerificationService;
    private final KakaoLoginService kakaoLoginService;
    private final SignupService signupService;
    private final EmailCheckService emailCheckService;
    private final LoginService loginService;
    private final MeService meService;
    private final LoginSuccessHandler loginSuccessHandler;
    private final PasswordResetService passwordResetService;
    private final SessionRevoker sessionRevoker;

    @Operation(summary = "CSRF 토큰 받기",
            description = "XSRF-TOKEN 쿠키를 내려준다. 앱을 처음 열 때와 로그아웃한 뒤에 부른다. 응답 204")
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
        csrfToken.getToken();
    }

    @Operation(summary = "인증번호 받기",
            description = "6자리 인증번호를 메일로 보낸다. 5분 유효. 같은 이메일은 60초 뒤에 다시 받을 수 있고 1시간에 5번까지. "
                    + "purpose가 SIGNUP이면 이미 가입된 이메일인지 확인한다. "
                    + "PASSWORD_RESET은 가입 여부와 상관없이 같은 응답이다 (가입되지 않은 이메일이면 메일만 가지 않는다)")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.DUPLICATE_EMAIL, ErrorCode.VERIFICATION_RESEND_TOO_SOON,
            ErrorCode.VERIFICATION_RATE_LIMITED, ErrorCode.EMAIL_SEND_FAILED})
    @PostMapping("/email/send-code")
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request, HttpSession session) {
        Duration validity = emailVerificationService.sendCode(session, request.email(), request.purpose());
        return new SendCodeResponse(validity.toSeconds());
    }

    @Operation(summary = "인증번호 확인",
            description = "맞으면 204. 인증 결과는 세션에 10분 동안 남고 가입, 카카오 가입, 비밀번호 재설정에 한 번 쓸 수 있다. 5번 틀리면 인증번호를 다시 받아야 한다")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.VERIFICATION_CODE_MISMATCH,
            ErrorCode.VERIFICATION_EXPIRED, ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED})
    @PostMapping("/email/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyCodeRequest request, HttpSession session) {
        emailVerificationService.verify(session, request.email(), request.code());
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

   
    @Operation(summary = "회원가입",
            description = "같은 세션에서 purpose SIGNUP으로 이메일 인증을 마친 뒤 부른다. 가입하면 바로 로그인된다 (로그인 상태 유지는 하지 않음). 비밀번호는 8~20자, 영문·숫자·특수문자 각각 1개 이상. 특수문자는 !@#$%^&*()_+-=[]{}:;,.?/\\ 만, 한글·공백 불가. 응답 201")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_PASSWORD_FORMAT, ErrorCode.REQUIRED_CONSENT_MISSING,
            ErrorCode.EMAIL_NOT_VERIFIED, ErrorCode.DUPLICATE_EMAIL})
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse signup(@Valid @RequestBody SignupRequest request,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession();
        User user = signupService.signup(request, session);
        emailVerificationService.clearVerified(session);
        loginSuccessHandler.login(UserPrincipal.from(user), false, httpRequest, httpResponse);
        return meService.getMe(user.getId());
    }

    @Operation(summary = "로그인",
            description = "rememberMe가 true면 90일 동안 로그인 상태를 유지한다 (FREMEMBER 쿠키). 5번 연속 틀리면 15분 동안 잠긴다. "
                    + "없는 이메일과 틀린 비밀번호는 같은 응답이다. 비밀번호가 없는 카카오 회원도 같은 응답이다")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_CREDENTIALS, ErrorCode.ACCOUNT_LOCKED})
    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest request,
                            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UserPrincipal principal = loginService.authenticate(request.email(), request.password());
        loginSuccessHandler.login(principal, request.rememberMeRequested(), httpRequest, httpResponse);
        return meService.getMe(principal.getUserId());
    }

    @Operation(summary = "비밀번호 재설정",
            description = "비밀번호를 잊었을 때. 같은 세션에서 purpose PASSWORD_RESET으로 이메일 인증을 마친 뒤 부른다. "
                    + "비밀번호가 없는 카카오 회원은 여기서 비밀번호를 만든다(그 뒤 이메일로도 로그인할 수 있다). "
                    + "바꾸면 모든 기기에서 로그아웃되고 로그인 잠금도 풀린다. 이 요청으로 로그인되지는 않는다. 응답 204")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.EMAIL_NOT_VERIFIED,
            ErrorCode.INVALID_PASSWORD_FORMAT, ErrorCode.SAME_AS_OLD_PASSWORD})
    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request, HttpSession session) {
        String email = passwordResetService.resetPassword(request.email(), request.newPassword(), session);
        // 인증 결과는 한 번만 쓴다. 비밀번호를 모르는 사람이 이미 들어와 있을 수 있어 모든 로그인을 끊는다
        emailVerificationService.clearVerified(session);
        sessionRevoker.revokeAll(email);
    }

    @Operation(summary = "카카오 로그인",
            description = "프론트가 카카오 인가 요청(kauth.kakao.com/oauth/authorize)으로 받은 code와 그때 쓴 redirectUri를 보낸다. "
                    + "가입한 카카오 회원이면 로그인하고 status LOGGED_IN과 me. "
                    + "처음이면 status SIGNUP_REQUIRED와 카카오 이메일·인증 여부를 주고 10분 안에 카카오 가입(kakao/signup)을 부른다. "
                    + "같은 이메일로 이메일 가입한 회원이 있으면 KAKAO_EMAIL_REGISTERED (계정을 합치지 않는다)")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_KAKAO_REDIRECT_URI, ErrorCode.KAKAO_LOGIN_FAILED,
            ErrorCode.KAKAO_UNAVAILABLE, ErrorCode.KAKAO_EMAIL_REGISTERED})
    @PostMapping("/kakao/login")
    public KakaoLoginResponse kakaoLogin(@Valid @RequestBody KakaoLoginRequest request,
                                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession();
        Optional<UserPrincipal> principal = kakaoLoginService.login(request.code(), request.redirectUri(), session);
        if (principal.isPresent()) {
            loginSuccessHandler.login(principal.get(), request.rememberMeRequested(), httpRequest, httpResponse);
            return KakaoLoginResponse.loggedIn(meService.getMe(principal.get().getUserId()));
        }
        PendingKakaoSignup pending = kakaoLoginService.pending(session);
        return KakaoLoginResponse.signupRequired(pending.email(), pending.emailVerified());
    }

    @Operation(summary = "카카오 가입",
            description = "카카오 로그인에서 SIGNUP_REQUIRED를 받은 같은 세션에서 동의를 받아 부른다. "
                    + "emailVerified가 true면 카카오 이메일로 바로 가입하고, false거나 이메일이 없으면 먼저 "
                    + "email/send-code(SIGNUP) → email/verify를 마치고 그 email을 보낸다. 비밀번호는 없다. "
                    + "가입하면 바로 로그인된다 (로그인 상태 유지는 하지 않음). 응답 201")
    @ApiErrorCodes({ErrorCode.VALIDATION_FAILED, ErrorCode.KAKAO_SIGNUP_EXPIRED, ErrorCode.REQUIRED_CONSENT_MISSING,
            ErrorCode.EMAIL_NOT_VERIFIED, ErrorCode.KAKAO_EMAIL_REGISTERED, ErrorCode.KAKAO_ACCOUNT_REGISTERED})
    @PostMapping("/kakao/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse kakaoSignup(@Valid @RequestBody KakaoSignupRequest request,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = kakaoLoginService.signup(request, httpRequest.getSession());
        loginSuccessHandler.login(UserPrincipal.from(user), false, httpRequest, httpResponse);
        return meService.getMe(user.getId());
    }
}
