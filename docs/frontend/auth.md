# 인증 규칙

로그인은 **서버 세션 + 쿠키**다. JWT나 `Authorization` 헤더는 쓰지 않는다. 토큰을 저장하거나 붙일 필요가 없고, 쿠키만 브라우저가 들고 다니면 된다.

## 쿠키

| 쿠키 | 언제 생기나 | JS에서 읽기 | 뜻 |
|---|---|---|---|
| `FSESSION` | 이메일 인증, 가입, 로그인, 카카오 로그인 | 못 읽음 (HttpOnly) | 서버 세션. **30분 동안 요청이 없으면 만료** |
| `FREMEMBER` | 로그인할 때 `rememberMe: true` | 못 읽음 (HttpOnly) | 로그인 상태 유지 90일. 세션이 끝나도 다음 요청에서 자동으로 다시 로그인된다 |
| `XSRF-TOKEN` | `GET /api/auth/csrf` | **읽어야 함** | CSRF 토큰. 아래 참고 |

모두 `SameSite=Lax`, `Path=/`. 운영은 `Secure`가 붙는다(HTTPS 전용).

## 모든 요청에 쿠키 포함

- 프록시(권장, [README.md](README.md))를 쓰면 같은 출처라 브라우저가 알아서 붙인다.
- 프록시 없이 다른 포트로 부르면 `fetch(url, { credentials: 'include' })` / axios `withCredentials: true`.
- `<img src="/api/stories/.../image">` 그림 요청에도 쿠키가 자동으로 붙는다. 그림을 따로 fetch할 필요 없다.

## CSRF

상태를 바꾸는 요청(POST, PUT, PATCH, DELETE)은 **로그인, 가입, 이메일 인증, 카카오 로그인까지 모두** CSRF 헤더가 필요하다. 없으면 403 `FORBIDDEN`.

1. **앱을 처음 열 때**와 **로그아웃한 뒤** `GET /api/auth/csrf`를 부른다 (204, 본문 없음). 응답으로 `XSRF-TOKEN` 쿠키가 생긴다.
2. 상태를 바꾸는 요청마다 그 쿠키 값을 그대로 `X-XSRF-TOKEN` 헤더로 보낸다.

```js
function xsrfToken() {
  return document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.split('=')[1];
}

async function api(method, url, body) {
  const headers = { 'Content-Type': 'application/json' };
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = xsrfToken();
  const res = await fetch(url, {
    method,
    headers,
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 401) { /* 로그인 화면으로 */ }
  return res;
}
```

- axios를 쓰면 기본값이 `xsrfCookieName: 'XSRF-TOKEN'`, `xsrfHeaderName: 'X-XSRF-TOKEN'`이라 같은 출처(프록시)에서는 따로 할 일이 없다. 다른 출처면 `withXSRFToken: true`도 켠다.
- 헤더에는 쿠키 값을 **가공하지 않고 그대로** 넣는다.
- 쿠키 값은 가입, 로그인 뒤에도 그대로다. 요청 직전에 쿠키에서 다시 읽으면 걱정할 것이 없다.
- 로그아웃하면 `XSRF-TOKEN`도 지워진다. 로그아웃 직후 `GET /api/auth/csrf`를 다시 부른다.

## 401이면 로그인 화면

로그인이 필요한 API를 로그인 없이(또는 세션 만료 후) 부르면 401과 `{"code": "UNAUTHORIZED", "message": "로그인이 필요해요."}`. 공통 처리에서 로그인 화면으로 보낸다.

- 앱을 열 때 로그인 여부는 `GET /api/me`로 본다. 200이면 로그인 상태, 401이면 비로그인.
- 로그인 실패는 401 `INVALID_CREDENTIALS`(이메일·비밀번호 틀림)라서 **같은 401이라도 `code`로 구분한다.** 로그인 화면에서 받은 401을 "로그인 화면으로 이동"으로 처리하지 않는다.
- 로그인이 필요 없는 API: `GET /api/auth/csrf`, `POST /api/auth/email/check`, `POST /api/auth/email/send-code`, `POST /api/auth/email/verify`, `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/password/reset`, `POST /api/auth/kakao/login`, `POST /api/auth/kakao/signup`, `POST /api/auth/logout`. 나머지는 모두 로그인 필요.

## 가입과 로그인 흐름 (이메일)

```
GET  /api/auth/csrf
POST /api/auth/email/check       {"email": "parent@example.com"}                          → 200 {"available": true}
POST /api/auth/email/send-code   {"email": "parent@example.com", "purpose": "SIGNUP"}     → 200 {"expiresInSeconds": 300}
POST /api/auth/email/verify      {"email": "parent@example.com", "code": "123456"}        → 204
POST /api/auth/signup            {email, password, consents, termsVersion}                → 201 (바로 로그인됨)

POST /api/auth/login             {"email", "password", "rememberMe": false}               → 200 (MeResponse)
POST /api/auth/logout                                                                      → 204 (그 뒤 GET /api/auth/csrf)
```

- 휴대폰 번호는 받지 않는다. **이메일 인증번호(6자리, 5분 유효)**로 본인을 확인한다. 메일 제목은 `[FIRSTORY] 인증번호를 알려 드려요`.
- **이메일 인증과 가입은 같은 브라우저 세션에서** 해야 한다. 인증 결과가 세션(`FSESSION`)에 10분 동안 남는다. 인증한 이메일과 가입 이메일이 달라도 `EMAIL_NOT_VERIFIED`.
- 같은 이메일은 60초 뒤에 다시 받을 수 있고(`VERIFICATION_RESEND_TOO_SOON`) 1시간에 5번까지(`VERIFICATION_RATE_LIMITED`). 메일 서버 장애면 503 `EMAIL_SEND_FAILED`.
- `send-code`(SIGNUP)는 이미 가입된 이메일이면 409 `DUPLICATE_EMAIL`(메일을 보내지 않는다).
- 비밀번호: 8~20자, 영문·숫자·특수문자 각각 1개 이상. 특수문자는 `!@#$%^&*()_+-=[]{}:;,.?/\`만, 한글·공백 불가.
- `consents`: `GUARDIAN`, `TERMS`, `PRIVACY`는 필수 `true`, `EMAIL_NOTIFY`(동화 제작 소식을 이메일로 받기)는 선택.
- 로그인 5번 연속 실패 시 15분 잠김(423 `ACCOUNT_LOCKED`).
- local, dev 서버(메일을 보내지 않는 `MAIL_MODE=log`)는 인증번호가 항상 `000000`이다. 실제로 메일을 보내는 서버는 무작위 번호다.

## 카카오 로그인

```
(브라우저) https://kauth.kakao.com/oauth/authorize?client_id={REST API 키}&redirect_uri={redirectUri}&response_type=code
          → 카카오 로그인·동의 → {redirectUri}?code=...

GET  /api/auth/csrf
POST /api/auth/kakao/login    {"code": "...", "redirectUri": "...", "rememberMe": false}
     → 200 {"status": "LOGGED_IN", "me": {...}}                                    가입한 카카오 회원. 로그인됨
     → 200 {"status": "SIGNUP_REQUIRED", "email": "...", "emailVerified": true}   처음. 가입 화면(동의)으로

(emailVerified가 false거나 email이 null이면 먼저)
POST /api/auth/email/send-code {"email": "...", "purpose": "SIGNUP"} → POST /api/auth/email/verify

POST /api/auth/kakao/signup   {"email": "...(emailVerified false일 때만)", "consents": {...}, "termsVersion": "..."}
     → 201 (MeResponse, 바로 로그인됨)
```

- `code`는 한 번만 쓸 수 있다. 실패하면(`KAKAO_LOGIN_FAILED`) 카카오 로그인부터 다시 한다. `redirectUri`는 인가 요청에 쓴 값과 **똑같이** 보낸다.
- `kakao/login`과 `kakao/signup`은 **같은 브라우저 세션에서 10분 안에** 부른다(넘으면 `KAKAO_SIGNUP_EXPIRED`).
- `emailVerified: true`(카카오가 인증한 이메일)면 우리 이메일 인증을 건너뛰고 카카오 이메일로 가입한다. 화면에는 그 이메일을 보여 주기만 한다.
- `emailVerified: false`거나 이메일이 없으면, 이메일 입력(카카오 이메일을 채워 둬도 된다) → 인증번호 → `kakao/signup`에 그 email을 보낸다.
- **같은 이메일로 이메일 가입한 회원이 있으면 계정을 합치지 않는다.** 409 `KAKAO_EMAIL_REGISTERED` → "이메일과 비밀번호로 로그인해 주세요"로 이메일 로그인 화면에 보낸다.
- 카카오 회원은 비밀번호가 없다(`me.hasPassword: false`, `me.kakaoLinked: true`). 이메일 로그인을 하려면 비밀번호 찾기로 비밀번호를 만든다.
- local은 가짜 카카오라 아무 `code`(예: `test1`)나 보내면 `kakao-test1@example.com`(인증된 이메일) 회원이 된다. `code`가 `fail`이면 `KAKAO_LOGIN_FAILED`.

## 비밀번호 찾기 (비밀번호 재설정)

```
GET  /api/auth/csrf
POST /api/auth/email/send-code   {"email": "parent@example.com", "purpose": "PASSWORD_RESET"}  → 200 {"expiresInSeconds": 300}
POST /api/auth/email/verify      {"email": "parent@example.com", "code": "123456"}             → 204
POST /api/auth/password/reset    {"email": "parent@example.com", "newPassword": "..."}         → 204
```

- **가입 여부를 알려 주지 않는다.** 가입되지 않은 이메일도 `send-code`는 똑같이 200이고 메일만 가지 않는다. 화면은 "가입된 이메일이라면 인증번호가 발송돼요"처럼 안내하고, 이메일이 없다는 문구를 만들지 않는다.
- 이메일 인증과 재설정은 같은 브라우저 세션에서 10분 안에 한다. 인증 결과는 한 번만 쓸 수 있다.
- 바꾸면 **모든 기기에서 로그아웃**되고(로그인 상태 유지 포함) 로그인 잠금도 풀린다. 이 요청으로 로그인되지는 않으니 로그인 화면으로 보낸다.
- 비밀번호가 없는 카카오 회원도 여기서 비밀번호를 만든다(그 뒤 이메일로도 로그인할 수 있다).
- 오류: `EMAIL_NOT_VERIFIED`(인증 안 함·10분 지남·다른 purpose), `INVALID_PASSWORD_FORMAT`, `SAME_AS_OLD_PASSWORD`. `SAME_AS_OLD_PASSWORD`면 인증은 남아 있어 새 비밀번호만 다시 받으면 된다.

## 민감 작업은 현재 비밀번호 필요

| 작업 | API | 본문 | 결과 |
|---|---|---|---|
| 비밀번호 변경 | `POST /api/me/password` | `{currentPassword, newPassword}` | 204. **다른 기기는 모두 로그아웃**, 지금 기기는 로그인 유지 |
| 탈퇴 | `POST /api/me/withdraw` | `{currentPassword}` | 204. 모든 기기 로그아웃. 다시 `GET /api/auth/csrf` |

현재 비밀번호가 틀리면 400 `CURRENT_PASSWORD_MISMATCH`. 비밀번호가 없는 카카오 회원(`me.hasPassword: false`)은 비밀번호 변경이 400 `PASSWORD_NOT_SET`(비밀번호 찾기로 만든다)이고, 탈퇴는 `{}`로 비밀번호 없이 한다.
