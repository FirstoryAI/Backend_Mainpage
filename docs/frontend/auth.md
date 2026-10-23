# 인증 규칙

로그인은 **서버 세션 + 쿠키**다. JWT나 `Authorization` 헤더는 쓰지 않는다. 토큰을 저장하거나 붙일 필요가 없고, 쿠키만 브라우저가 들고 다니면 된다.

## 쿠키

| 쿠키 | 언제 생기나 | JS에서 읽기 | 뜻 |
|---|---|---|---|
| `FSESSION` | 문자 인증, 가입, 로그인 | 못 읽음 (HttpOnly) | 서버 세션. **30분 동안 요청이 없으면 만료** |
| `FREMEMBER` | 로그인할 때 `rememberMe: true` | 못 읽음 (HttpOnly) | 로그인 상태 유지 90일. 세션이 끝나도 다음 요청에서 자동으로 다시 로그인된다 |
| `XSRF-TOKEN` | `GET /api/auth/csrf` | **읽어야 함** | CSRF 토큰. 아래 참고 |

모두 `SameSite=Lax`, `Path=/`. 운영은 `Secure`가 붙는다(HTTPS 전용).

## 모든 요청에 쿠키 포함

- 프록시(권장, [README.md](README.md))를 쓰면 같은 출처라 브라우저가 알아서 붙인다.
- 프록시 없이 다른 포트로 부르면 `fetch(url, { credentials: 'include' })` / axios `withCredentials: true`.
- `<img src="/api/stories/.../image">` 그림 요청에도 쿠키가 자동으로 붙는다. 그림을 따로 fetch할 필요 없다.

## CSRF

상태를 바꾸는 요청(POST, PUT, PATCH, DELETE)은 **로그인, 가입, 문자 인증까지 모두** CSRF 헤더가 필요하다. 없으면 403 `FORBIDDEN`.

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
- 로그인이 필요 없는 API: `GET /api/auth/csrf`, `POST /api/auth/phone/send-code`, `POST /api/auth/phone/verify`, `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/logout`. 나머지는 모두 로그인 필요.

## 가입과 로그인 흐름

```
GET  /api/auth/csrf
POST /api/auth/phone/send-code   {"phone": "010-1234-5678", "purpose": "SIGNUP"}   → 200 {"expiresInSeconds": 180}
POST /api/auth/phone/verify      {"phone": "010-1234-5678", "code": "000000"}      → 204
POST /api/auth/signup            {email, password, phone, consents, termsVersion}  → 201 (바로 로그인됨)

POST /api/auth/login             {"email", "password", "rememberMe": false}        → 200 (MeResponse)
POST /api/auth/logout                                                               → 204 (그 뒤 GET /api/auth/csrf)
```

- **문자 인증과 가입은 같은 브라우저 세션에서** 해야 한다. 인증 결과가 세션(`FSESSION`)에 10분 동안 남는다.
- 휴대폰 번호는 `010-0000-0000` 형식(하이픈 포함).
- 비밀번호: 8~64자, 영문과 숫자 각각 1개 이상 (임시 규칙).
- `consents`: `GUARDIAN`, `TERMS`, `PRIVACY`는 필수 `true`, `SMS_NOTIFY`는 선택.
- 로그인 5번 연속 실패 시 15분 잠김(423 `ACCOUNT_LOCKED`).
- 응답에 휴대폰 번호는 가린 형태(`phoneMasked: "010-****-5678"`)로만 온다.

## 민감 작업은 현재 비밀번호 필요

| 작업 | API | 본문 | 결과 |
|---|---|---|---|
| 비밀번호 변경 | `POST /api/me/password` | `{currentPassword, newPassword}` | 204. **다른 기기는 모두 로그아웃**, 지금 기기는 로그인 유지 |
| 휴대폰 번호 변경 | `POST /api/me/phone` | `{currentPassword, phone}` | 204. 먼저 같은 세션에서 `send-code`(`purpose: "CHANGE_PHONE"`) → `verify`를 마쳐야 한다 |
| 탈퇴 | `POST /api/me/withdraw` | `{currentPassword}` | 204. 모든 기기 로그아웃. 다시 `GET /api/auth/csrf` |

현재 비밀번호가 틀리면 400 `CURRENT_PASSWORD_MISMATCH`.
