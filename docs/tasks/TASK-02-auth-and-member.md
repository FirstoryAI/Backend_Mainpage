# TASK-02. 인증과 회원

`CLAUDE.md`를 먼저 읽고 시작한다. 이 작업이 끝나면 회원가입(문자 인증 포함), 로그인, 로그인 상태 유지, 로그아웃, 내 정보 관리가 API로 동작해야 한다.

## 범위

**포함**: Spring Security 설정, 세션(Spring Session JDBC), remember-me, CSRF, 문자 인증, 가입, 로그인, 로그아웃, 내 정보, 비밀번호 변경, 휴대폰 변경, 탈퇴, 로그인 실패 잠금, 문자 발송 제한, 통합 테스트

**제외** (다음 작업): 아이 프로필(TASK-03), 동화 만들기(TASK-04~), 실제 문자 발송 대행사 연동(솔라피 등, TASK-07)

## 결정 사항 (바꾸지 않는다)

| 항목 | 결정 |
|---|---|
| 로그인 수단 | 이메일 + 비밀번호. 가입 시 문자 인증한 휴대폰 번호 필수. 카카오 로그인 없음 |
| 로그인 유지 | 서버 세션. **JWT를 쓰지 않는다** |
| 세션 저장소 | PostgreSQL (Spring Session JDBC). 서버가 여러 대여도 같은 세션을 읽는다 |
| 재방문 | Remember-me, persistent token 방식, **90일**, 체크박스 기본값 꺼짐 |
| 세션 유휴 만료 | 30분 |
| 쿠키 | HttpOnly, Secure(운영), SameSite=Lax |
| CSRF | 켠다. SPA 방식(쿠키로 토큰 전달, 요청 헤더로 반환) |
| 민감 작업 재확인 | 비밀번호 변경, 휴대폰 변경, 탈퇴는 현재 비밀번호를 다시 받는다 |
| 즉시 무효화 | 비밀번호 변경, 탈퇴 시 그 회원의 **모든 세션과 remember-me 토큰 삭제** |
| 로그인 잠금 | 5회 연속 실패 시 15분 잠금 |
| 문자 발송 제한 | 같은 번호에 1시간 5회. 재발송은 60초 뒤부터 |
| 인증번호 | 6자리 숫자, 3분 유효, 5회 틀리면 무효. **세션에 저장** (Redis 사용 안 함) |
| Principal 이름 | 로그인 이메일. Spring Session 인덱스와 remember-me 토큰이 이 값을 쓴다 |

## 도메인과 주소

서비스 도메인은 `firstoryofficial`이다. **[확인 필요] 최상위 도메인(.com, .kr, .co.kr 중 무엇인지)**. 이 문서는 `firstoryofficial.com`으로 가정하고, 코드에는 하드코딩하지 않고 환경 변수로만 받는다.

권장 배포 구조 (같은 출처, CORS 불필요):

```
https://firstoryofficial.com          프론트
https://firstoryofficial.com/api/**   백엔드 (리버스 프록시)
```

API를 서브도메인(`api.firstoryofficial.com`)으로 분리하더라도 동작하도록 설정을 열어 둔다.

| 환경 변수 | 로컬 기본값 | 운영 예시 | 용도 |
|---|---|---|---|
| `COOKIE_SECURE` | `false` | `true` | 쿠키 Secure 속성 |
| `COOKIE_DOMAIN` | (비움) | (비움) 또는 `firstoryofficial.com` | 서브도메인 분리 시에만 설정 |
| `ALLOWED_ORIGINS` | `http://localhost:5173` | (비움) 또는 `https://firstoryofficial.com` | CORS. 같은 출처 배포면 비움 |
| `REMEMBER_ME_KEY` | 로컬용 임의값 | 비밀값 | remember-me 서명 키 |

- `COOKIE_DOMAIN`이 비어 있으면 Domain 속성을 쓰지 않는다 (host-only 쿠키가 더 안전하다).
- `ALLOWED_ORIGINS`가 비어 있으면 CORS 설정을 등록하지 않는다. 값이 있으면 그 출처만 허용하고 `allowCredentials(true)`.

---

## 작업 순서

각 단계 끝에 `./gradlew build` 통과 후 커밋. 마지막에 push.

1. 의존성 정리와 V2 마이그레이션
2. 오류 코드와 입력 검증 처리
3. 보안 설정 (Security, 세션, CSRF, remember-me)
4. 문자 인증
5. 가입
6. 로그인, 로그아웃, 잠금
7. 내 정보, 비밀번호 변경, 휴대폰 변경, 탈퇴
8. 통합 테스트, 기동 확인, 문서 갱신

---

## 1. 의존성 정리와 V2 마이그레이션

### 추가

- Spring Security 스타터
- Spring Session JDBC (Spring Boot 4의 세션 JDBC 스타터 또는 자동 설정 모듈)
- 테스트: `spring-security-test`

### 제거

- **Redis 스타터와 docker-compose의 redis 서비스, application.yml의 redis 설정을 제거한다.** 인증번호는 세션에, 세션은 PostgreSQL에 저장하므로 Redis가 필요 없다. CLAUDE.md의 스택과 명령어 섹션도 고친다.
- `spring-security-crypto` 단독 의존성은 Security 스타터에 포함되므로 제거한다.

### 주의

- 아티팩트 이름은 TASK-01처럼 **추측하지 말고 Spring Boot 4.1.1 BOM에서 확인**한다. 버전은 적지 않는다.
- Spring Session의 자동 스키마 생성을 끈다: `spring.session.jdbc.initialize-schema: never`. 스키마는 Flyway가 만든다.

### V2

`src/main/resources/db/migration/V2__auth_session.sql`은 **이미 제공되어 있다. 내용을 바꾸지 않는다.**

| 테이블 | 용도 | JPA 엔티티 |
|---|---|---|
| `spring_session`, `spring_session_attributes` | 세션 | 만들지 않음 (Spring Session이 직접 사용) |
| `persistent_logins` | remember-me 토큰 | 만들지 않음 (`JdbcTokenRepositoryImpl`이 직접 사용) |
| `users`에 `failed_login_count`, `locked_until` 추가 | 로그인 잠금 | `User`에 필드 추가 |
| `sms_send_logs` | 번호별 발송 횟수 제한 | `SmsSendLog` 엔티티 추가 |

---

## 2. 오류 코드와 입력 검증 처리

### ErrorCode 추가

| 코드 | HTTP | 메시지 |
|---|---|---|
| VALIDATION_FAILED | 400 | 입력값을 다시 확인해 주세요. |
| UNAUTHORIZED | 401 | 로그인이 필요해요. |
| FORBIDDEN | 403 | 접근할 수 없어요. |
| INVALID_CREDENTIALS | 401 | 이메일 또는 비밀번호가 맞지 않아요. |
| ACCOUNT_LOCKED | 423 | 로그인을 여러 번 실패해서 15분 동안 잠겼어요. 잠시 후 다시 시도해 주세요. |
| INVALID_PASSWORD_FORMAT | 400 | 비밀번호는 영문과 숫자를 포함해 8~64자로 입력해 주세요. |
| CURRENT_PASSWORD_MISMATCH | 400 | 현재 비밀번호가 맞지 않아요. |
| SAME_AS_OLD_PASSWORD | 400 | 지금 쓰는 비밀번호와 다른 비밀번호를 입력해 주세요. |
| SMS_RATE_LIMITED | 429 | 인증번호를 너무 많이 요청했어요. 1시간 후 다시 시도해 주세요. |
| SMS_RESEND_TOO_SOON | 429 | 인증번호는 1분 뒤에 다시 받을 수 있어요. |
| VERIFICATION_CODE_MISMATCH | 400 | 인증번호가 맞지 않아요. |
| VERIFICATION_EXPIRED | 400 | 인증번호가 만료됐어요. 다시 받아 주세요. |
| VERIFICATION_ATTEMPTS_EXCEEDED | 429 | 인증번호를 5번 틀렸어요. 인증번호를 다시 받아 주세요. |
| PHONE_NOT_VERIFIED | 400 | 휴대폰 인증을 먼저 해 주세요. |

### 검증 처리

- `GlobalExceptionHandler`에 `MethodArgumentNotValidException` 처리를 추가한다. 응답은 `{code: "VALIDATION_FAILED", message, fields: [{field, message}]}`.
- **INVALID_CREDENTIALS는 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다** (가입 여부 노출 방지).

---

## 3. 보안 설정

### SecurityFilterChain

- `formLogin`, `httpBasic` 끄기. 로그인은 JSON API로 직접 처리한다 (6장).
- 경로 권한:

| 경로 | 권한 |
|---|---|
| `POST /api/auth/phone/send-code`, `POST /api/auth/phone/verify` | 모두 |
| `POST /api/auth/signup`, `POST /api/auth/login` | 모두 |
| `GET /api/auth/csrf` | 모두 |
| `GET /actuator/health` (있다면) | 모두 |
| 그 외 `/api/**` | 로그인 필요 |

- 인증 실패와 권한 실패는 HTML 리다이렉트가 아니라 JSON으로: `401 {code: "UNAUTHORIZED"}`, `403 {code: "FORBIDDEN"}`.
- 세션 고정 공격 방지: 로그인 성공 시 세션 ID를 바꾼다.

### CSRF (SPA 방식)

- 토큰을 `XSRF-TOKEN` 쿠키(HttpOnly 아님)로 내려주고, 프론트는 상태를 바꾸는 요청에 `X-XSRF-TOKEN` 헤더로 돌려보낸다.
- Spring Security가 제공하는 SPA용 CSRF 설정을 쓴다 (버전에 맞는 공식 방식을 확인해서 적용).
- `GET /api/auth/csrf`: 204. 프론트가 앱을 처음 열 때 호출해서 CSRF 쿠키를 받는다.
- **로그인, 가입 요청도 CSRF를 검사한다** (제외하지 않는다).

### 세션

- Spring Session JDBC, 유휴 만료 30분.
- 세션 쿠키: 이름 `FSESSION`, HttpOnly, `Secure=${COOKIE_SECURE}`, SameSite=Lax, Path `/`, Domain은 `COOKIE_DOMAIN`이 있을 때만.
- 회원별 세션을 찾아 지울 수 있도록 `FindByIndexNameSessionRepository`를 사용한다 (principal 이름 = 이메일).

### Remember-me

- `PersistentTokenBasedRememberMeServices` + `JdbcTokenRepositoryImpl`(테이블 생성 옵션 끔).
- 쿠키 이름 `FREMEMBER`, 90일, HttpOnly, Secure, SameSite=Lax.
- 로그인 API가 JSON이라 요청 파라미터로 remember-me 여부를 넘길 수 없다. **`rememberMeRequested`를 재정의해서 요청 속성(request attribute) 값을 보도록** 한 하위 클래스를 만든다. 로그인 서비스가 `rememberMe: true`일 때 이 속성을 넣고 `loginSuccess`를 호출한다.
- remember-me로 자동 로그인된 요청에서도 민감 작업은 현재 비밀번호를 받으므로 추가 제한은 두지 않는다.

### 로그인 사용자

- `UserPrincipal(userId, email, passwordHash)` 구현 `UserDetails`. `getUsername()`은 이메일.
- `UserDetailsService`는 이메일로 `User`를 찾는다. 탈퇴한 회원은 `@SQLRestriction` 때문에 자동으로 안 찾아진다.
- 컨트롤러에서는 `@AuthenticationPrincipal UserPrincipal`로 userId를 꺼낸다.

---

## 4. 문자 인증

### SmsSender 인터페이스

```java
public interface SmsSender {
    void send(PhoneNumber to, String text);
}
```

- 이번 작업에서는 **`LoggingSmsSender`만** 만든다. `local`, `test` 프로필에서만 빈으로 등록. 로그에 `[SMS] 010-****-5678 ← 인증번호 123456`처럼 **번호는 가리고 인증번호만** 보여준다.
- 운영 프로필에서 `SmsSender` 빈이 없으면 기동이 실패해야 한다 (실제 대행사 연동은 TASK-07).

### 인증 흐름

```
POST /api/auth/phone/send-code   {phone, purpose}
POST /api/auth/phone/verify      {phone, code}
```

- `purpose`: `SIGNUP` | `CHANGE_PHONE`
- `send-code`
  - `PhoneNumber.fromDisplay`로 형식 검사
  - `SIGNUP`이면 이미 가입된 번호인지 `phone_hash`로 확인 → `DUPLICATE_PHONE`
  - 제한 검사: `sms_send_logs`에서 같은 `phone_hash`가 최근 1시간 5회 이상이면 `SMS_RATE_LIMITED`, 최근 60초 안에 보낸 적 있으면 `SMS_RESEND_TOO_SOON`
  - `SecureRandom`으로 6자리 생성, 세션에 `PhoneVerification(phoneDigits, purpose, codeHash, expiresAt, attempts=0)` 저장. **코드 평문을 세션에 두지 않고 SHA-256 해시로 저장**
  - 문자 발송, `sms_send_logs` 기록
  - 응답 `200 {expiresInSeconds: 180}`
- `verify`
  - 세션의 인증 정보가 없거나 번호가 다르면 `VERIFICATION_EXPIRED`
  - 만료면 `VERIFICATION_EXPIRED`, 시도 5회 초과면 `VERIFICATION_ATTEMPTS_EXCEEDED`
  - 틀리면 attempts +1 후 `VERIFICATION_CODE_MISMATCH`
  - 맞으면 세션에 `VerifiedPhone(phoneDigits, purpose, verifiedAt)` 저장, 인증 정보는 삭제. 응답 `204`
- `VerifiedPhone`은 **10분** 동안만 가입이나 번호 변경에 쓸 수 있고, 한 번 쓰면 세션에서 지운다.
- 세션에 저장하는 객체는 `Serializable` record로 만든다 (Spring Session JDBC가 직렬화해서 저장).

---

## 5. 가입

```
POST /api/auth/signup
```

```json
{
  "email": "parent@example.com",
  "password": "firstory123",
  "phone": "010-1234-5678",
  "consents": { "GUARDIAN": true, "TERMS": true, "PRIVACY": true, "SMS_NOTIFY": false },
  "termsVersion": "2026-10-01"
}
```

처리 순서:

1. Bean Validation: 이메일 형식, 필수값
2. 비밀번호 규칙: 8~64자, 영문과 숫자 각각 1개 이상 → `INVALID_PASSWORD_FORMAT`  // TODO(PM): 비밀번호 규칙 확정
3. `ConsentPolicy.validateRequired`
4. 세션의 `VerifiedPhone`이 있고, purpose가 SIGNUP이고, 번호가 같고, 10분 안인지 → 아니면 `PHONE_NOT_VERIFIED`
5. 이메일 중복 → `DUPLICATE_EMAIL`, 번호 중복 → `DUPLICATE_PHONE` (인증 후 그사이 누가 가입했을 수 있음)
6. `User.signUp` (BCrypt, `PhoneCrypto`), 동의 4건을 `UserConsent.record`로 저장
7. DB 부분 UNIQUE 인덱스 위반(`DataIntegrityViolationException`)도 `DUPLICATE_EMAIL`/`DUPLICATE_PHONE`으로 바꿔 응답 (동시 가입 대비)
8. **가입 직후 자동 로그인** (6장의 로그인 성공 처리와 같은 코드, remember-me는 하지 않음). 세션에서 `VerifiedPhone` 삭제
9. 응답 `201` + `MeResponse` (7장)

---

## 6. 로그인, 로그아웃, 잠금

```
POST /api/auth/login    {email, password, rememberMe}
POST /api/auth/logout
```

### 로그인

1. 이메일로 사용자 조회. 없으면 `INVALID_CREDENTIALS`
2. `locked_until`이 지금보다 뒤면 `ACCOUNT_LOCKED` (비밀번호 검사도 하지 않음)
3. `AuthenticationManager`로 인증
   - 실패: `User.recordLoginFailure(now)` → 실패 횟수 +1, 5회가 되면 `locked_until = now + 15분`, 횟수 0으로. `INVALID_CREDENTIALS`
   - 성공: `User.recordLoginSuccess()` → 횟수 0, `locked_until` null
4. 성공 처리 (가입과 공용 `LoginSuccessHandler` 같은 컴포넌트로 묶는다)
   - 새 `SecurityContext`에 인증 정보를 넣고 `SecurityContextRepository`로 **명시적으로 저장**
   - 세션 ID 변경 (세션 고정 공격 방지)
   - `rememberMe`가 true면 remember-me 발급
5. 응답 `200` + `MeResponse`

- 잠금 횟수 변경은 인증 실패 예외가 나도 커밋되도록 트랜잭션을 분리한다.
- `User`에 `failedLoginCount`, `lockedUntil` 필드와 `recordLoginFailure`, `recordLoginSuccess`, `isLocked(now)` 메서드를 추가한다. 잠금 기준값(5회, 15분)은 `User`의 상수로 둔다.

### 로그아웃

- 세션 무효화, remember-me 토큰 삭제(그 기기 것만), `FSESSION`·`FREMEMBER` 쿠키 삭제
- 응답 `204`. 로그인 안 한 상태에서 불러도 `204`

---

## 7. 내 정보

모두 로그인 필요.

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/api/me` | - | `MeResponse` |
| POST | `/api/me/onboarding-seen` | - | 204 |
| PUT | `/api/me/consents/sms-notify` | `{agreed}` | 204 |
| POST | `/api/me/password` | `{currentPassword, newPassword}` | 204 |
| POST | `/api/me/phone` | `{currentPassword, phone}` | 204 |
| POST | `/api/me/withdraw` | `{currentPassword}` | 204 |

`MeResponse`:

```json
{
  "id": 1,
  "email": "parent@example.com",
  "phoneMasked": "010-****-5678",
  "onboardingSeen": false,
  "smsNotify": false
}
```

- 휴대폰 번호는 **가린 형태만** 내려준다. 전체 번호를 응답하는 API는 만들지 않는다.
- `sms-notify`: 동의하면 새 `UserConsent.record`(agreed true), 철회하면 최신 동의 행에 `withdraw`. `smsNotify`는 최신 행의 `isActive()`.
- `password`: 현재 비밀번호 확인 → 규칙 검사 → 같은 비밀번호면 `SAME_AS_OLD_PASSWORD` → 변경 → **이 회원의 모든 세션과 remember-me 토큰 삭제** → 지금 요청한 기기는 다시 로그인 처리해서 로그인 상태 유지 (remember-me는 새로 발급하지 않음)
- `phone`: 현재 비밀번호 확인 → 세션의 `VerifiedPhone`(purpose `CHANGE_PHONE`) 확인 → 중복 확인 → `User.changePhone`
- `withdraw`: 현재 비밀번호 확인 → `User.withdraw` → 모든 세션과 remember-me 토큰 삭제 → 쿠키 삭제. 아이, 동화 데이터 처리 방식은 `// TODO(PM): PRD 36 파기 정책`으로 남긴다.

### 세션 일괄 삭제

`SessionRevoker` 같은 컴포넌트 하나로 묶는다.
- `revokeAll(email)`: `FindByIndexNameSessionRepository.findByPrincipalName(email)`로 찾은 세션 모두 삭제 + `PersistentTokenRepository.removeUserTokens(email)`

---

## 8. 테스트

### 단위 테스트

| 대상 | 확인할 것 |
|---|---|
| 비밀번호 규칙 | 8자 미만, 64자 초과, 숫자 없음, 영문 없음 → 예외 / `firstory123` 통과 |
| `User` 잠금 | 4회 실패는 잠기지 않음 / 5회째 잠김 / 15분 뒤 `isLocked` false / 성공 시 초기화 |
| 인증번호 | 6자리 숫자 / 해시로 저장되고 평문이 없음 / 만료, 시도 초과 판정 |

### 통합 테스트 (`@SpringBootTest` + MockMvc + Testcontainers PostgreSQL)

실제 Security 필터, 세션, Flyway V1·V2가 모두 적용된 상태에서 돈다.

| 시나리오 | 확인할 것 |
|---|---|
| 가입 전체 흐름 | csrf → send-code → (테스트용 SmsSender에서 코드 읽기) → verify → signup → `GET /api/me` 200 |
| 인증 없이 가입 | `PHONE_NOT_VERIFIED` |
| 필수 동의 누락 | `REQUIRED_CONSENT_MISSING` |
| 중복 이메일, 중복 번호 | 409 |
| CSRF 토큰 없이 로그인 | 403 |
| 로그인 실패 메시지 | 없는 이메일과 틀린 비밀번호의 응답이 완전히 같음 |
| 5회 실패 | 6번째는 맞는 비밀번호여도 `ACCOUNT_LOCKED` |
| remember-me | `rememberMe: true`면 `FREMEMBER` 쿠키 발급, `persistent_logins`에 행 생김 / false면 없음 |
| remember-me 재방문 | 세션 쿠키 없이 `FREMEMBER`만으로 `GET /api/me` 200 |
| 비밀번호 변경 | 다른 세션(기기 B)은 401이 되고, 변경한 기기 A는 계속 200 / remember-me 토큰 삭제됨 |
| 탈퇴 | 모든 세션 401 / 같은 이메일로 재가입 가능 |
| 문자 발송 제한 | 같은 번호 6번째 요청 `SMS_RATE_LIMITED` / 60초 안 재요청 `SMS_RESEND_TOO_SOON` |
| 인증번호 5회 틀림 | `VERIFICATION_ATTEMPTS_EXCEEDED`, 그 뒤 맞는 코드도 실패 |
| 응답의 휴대폰 번호 | `010-****-5678` 형태만 |
| 로그 | 테스트 로그 출력에 전체 휴대폰 번호가 없음 (가능하면 OutputCaptureExtension으로 확인) |

- 테스트에서 시간 제한(60초, 3분, 10분, 15분)을 확인하려면 `Clock`을 빈으로 주입받아 쓰고, 테스트에서 고정·이동 가능한 Clock으로 바꾼다. **`Instant.now()`를 코드에서 직접 부르지 않는다.**

### 기동 확인

```bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=local'
```

Flyway V2 적용, validate 통과, Redis 없이 기동되면 완료.

### 문서 갱신

- `CLAUDE.md`: 스택에서 Redis 제거, 인증 방식(세션, remember-me 90일) 추가, 환경 변수 표 추가, 작업 문서 목록에 TASK-02 추가
- `application-local.yml.example`, `.env.example`에 새 환경 변수 반영

---

## 완료 조건

- [ ] `./gradlew build` 통과 (TASK-01 테스트 포함 전부)
- [ ] V1, V2 SQL 내용을 바꾸지 않았다
- [ ] Redis 의존성, 설정, compose 서비스가 모두 없다
- [ ] JWT 관련 코드나 의존성이 없다
- [ ] 응답과 로그 어디에도 전체 휴대폰 번호, 인증번호 평문(로컬 LoggingSmsSender 제외), 비밀번호가 없다
- [ ] 코드에서 `Instant.now()`를 직접 부르지 않고 `Clock`을 쓴다
- [ ] 도메인(`firstoryofficial`)과 출처는 환경 변수로만 받는다
- [ ] 미정 사항은 `TODO(PM)`으로 남겼다

## 패키지 위치

새 클래스는 `CLAUDE.md`의 도메인별 계층 구조를 따른다.

| 위치 | 클래스 예 |
|---|---|
| `global.config` | `SecurityConfig`, `SessionConfig`, `CorsConfig`, `ClockConfig` |
| `global.security` | `UserPrincipal`, `UserDetailsServiceImpl`, `JsonRememberMeServices`, `SessionRevoker`, 401/403 JSON 핸들러 |
| `global.sms` | `SmsSender`, `LoggingSmsSender` |
| `domain.auth.controller` | `AuthController` (csrf, phone, signup, login, logout) |
| `domain.auth.service` | `PhoneVerificationService`, `SignupService`, `LoginService` |
| `domain.auth.dto` | 요청·응답 record |
| `domain.auth.entity` | `SmsSendLog`, `SmsPurpose`, `PhoneVerification`, `VerifiedPhone`, `PasswordPolicy` |
| `domain.auth.repository` | `SmsSendLogRepository` |
| `domain.user.controller` | `MeController` |
| `domain.user.service` | `MeService` |
| `domain.user.dto` | `MeResponse` 등 |
