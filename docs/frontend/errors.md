# 오류 코드

모든 오류 응답은 같은 모양이다.

```json
{ "code": "STORY_NOT_FOUND", "message": "동화를 찾을 수 없어요." }
```

- **분기는 `code`로 한다.** HTTP 상태는 같은 401이라도 `UNAUTHORIZED`(로그인 화면으로)와 `INVALID_CREDENTIALS`(로그인 실패 안내)처럼 뜻이 다르다.
- `message`는 사용자에게 그대로 보여 줘도 되는 한국어 문장이다. 문구는 바뀔 수 있으니 `message`로 분기하지 않는다.
- 그림 API도 오류는 JSON으로 온다(`<img>`에서는 그냥 깨진 그림으로 보인다).
- 이 표는 `ErrorCode` enum과 테스트(`FrontendErrorsDocTest`)로 맞춘다. 코드가 추가되면 이 문서도 함께 고쳐야 빌드가 통과한다.

| 코드 | HTTP | 메시지 | 주로 나오는 API, 프론트 처리 |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | 입력값을 다시 확인해 주세요. | 본문·쿼리 형식이 틀린 모든 요청 (필수값 없음, 글자 수, 잘못된 enum 값 등) |
| `UNAUTHORIZED` | 401 | 로그인이 필요해요. | 로그인이 필요한 모든 API (로그인 안 함, 세션 만료) → 로그인 화면 |
| `FORBIDDEN` | 403 | 접근할 수 없어요. | 상태를 바꾸는 모든 요청 (POST/PUT/PATCH/DELETE)에 `X-XSRF-TOKEN`이 없거나 틀림 → `GET /api/auth/csrf` 후 다시 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호가 맞지 않아요. | `POST /api/auth/login` |
| `ACCOUNT_LOCKED` | 423 | 로그인을 여러 번 실패해서 15분 동안 잠겼어요. 잠시 후 다시 시도해 주세요. | `POST /api/auth/login` (5번 연속 실패, 15분) |
| `INVALID_PASSWORD_FORMAT` | 400 | 비밀번호는 영문과 숫자를 포함해 8~64자로 입력해 주세요. | `POST /api/auth/signup`, `POST /api/me/password` |
| `CURRENT_PASSWORD_MISMATCH` | 400 | 현재 비밀번호가 맞지 않아요. | `POST /api/me/password`, `POST /api/me/phone`, `POST /api/me/withdraw` |
| `SAME_AS_OLD_PASSWORD` | 400 | 지금 쓰는 비밀번호와 다른 비밀번호를 입력해 주세요. | `POST /api/me/password` |
| `SMS_RATE_LIMITED` | 429 | 인증번호를 너무 많이 요청했어요. 1시간 후 다시 시도해 주세요. | `POST /api/auth/phone/send-code` |
| `SMS_RESEND_TOO_SOON` | 429 | 인증번호는 1분 뒤에 다시 받을 수 있어요. | `POST /api/auth/phone/send-code` |
| `VERIFICATION_CODE_MISMATCH` | 400 | 인증번호가 맞지 않아요. | `POST /api/auth/phone/verify` |
| `VERIFICATION_EXPIRED` | 400 | 인증번호가 만료됐어요. 다시 받아 주세요. | `POST /api/auth/phone/verify` (3분 지남, 다른 번호, 받은 적 없음) |
| `VERIFICATION_ATTEMPTS_EXCEEDED` | 429 | 인증번호를 5번 틀렸어요. 인증번호를 다시 받아 주세요. | `POST /api/auth/phone/verify` (5번 틀림 → 다시 받기) |
| `PHONE_NOT_VERIFIED` | 400 | 휴대폰 인증을 먼저 해 주세요. | `POST /api/auth/signup`, `POST /api/me/phone` (인증 안 함, 10분 지남, 다른 세션) |
| `REQUIRED_CONSENT_MISSING` | 400 | 필수 항목에 모두 동의해야 가입할 수 있어요. | `POST /api/auth/signup` |
| `INVALID_PHONE_FORMAT` | 400 | 010-0000-0000 형식으로 입력해 주세요. | `send-code`, `verify`, `signup`, `POST /api/me/phone` |
| `DUPLICATE_EMAIL` | 409 | 이미 가입된 이메일이에요. | `POST /api/auth/signup` |
| `DUPLICATE_PHONE` | 409 | 이미 가입된 휴대폰 번호예요. | `send-code`(SIGNUP), `signup`, `POST /api/me/phone` |
| `INTERESTS_LIMIT_EXCEEDED` | 400 | 좋아하는 것은 고른 것과 직접 입력을 합쳐 5개까지예요. | `POST/PUT /api/children`, `PUT /api/stories/{id}/child` |
| `CUSTOM_VALUE_REQUIRED` | 400 | 기타를 고르면 내용을 직접 입력해 주세요. | `POST/PUT /api/children`, `PUT /api/stories/{id}/child`, `PUT /api/stories/{id}/situation` |
| `CUSTOM_VALUE_TOO_LONG` | 400 | 직접 입력은 {n}자까지 쓸 수 있어요. | 같은 곳. `{n}` 자리에 항목별 글자 수가 채워져 온다 (성향 20, 좋아하는 것 20, 상황 분류·반응 30) |
| `CHILD_LABEL_EXHAUSTED` | 400 | 저장할 수 있는 아이 수를 넘었어요. | `POST /api/children`, `PUT /api/stories/{id}/child` (호칭 아이 A~Z를 다 씀) |
| `INVALID_CHILD_NAME` | 400 | 아이 호칭은 1~30자로 입력해 주세요. | `PATCH /api/children/{id}/display-name` |
| `CHILD_NOT_FOUND` | 404 | 아이 정보를 찾을 수 없어요. | `/api/children/{id}` 전부, `PUT /api/stories/{id}/child` (없거나 다른 회원의 아이) |
| `INVALID_BIRTH_YM` | 400 | 생년월을 다시 확인해 주세요. | `POST/PUT /api/children`, `PUT /api/stories/{id}/child` (형식 `yyyy-MM`, 미래, 범위 밖) |
| `STORY_NOT_FOUND` | 404 | 동화를 찾을 수 없어요. | `/api/stories/{id}` 아래 전부 (없음, 지움, 다른 회원의 동화) |
| `INVALID_STATUS_TRANSITION` | 409 | 지금 상태에서는 할 수 없는 작업이에요. | 지금 상태에서 못 하는 요청 전부. 예: DRAFT 아닌데 입력, 확정 전 가이드·읽기, 만드는 중 삭제, 끝난 세션 쪽 넘김 → 상세를 다시 불러 화면을 맞춘다 |
| `DESCRIPTION_TOO_LONG` | 400 | 상황 설명은 60자까지 쓸 수 있어요. | `PUT /api/stories/{id}/situation` (60자) |
| `MOMENT_ONLY_FOR_PAST` | 400 | 아이의 반응과 감정은 있었던 일에서만 고를 수 있어요. | `PUT /api/stories/{id}/situation` (UPCOMING인데 reactions·emotions를 보냄) |
| `VERSION_NOT_IN_STORY` | 400 | 이 동화의 버전이 아니에요. | `PUT /api/stories/{id}/current-version` 내부 검사. 보통은 그 전에 `VERSION_NOT_FOUND`가 난다 |
| `REGENERATION_LIMIT_REACHED` | 409 | 다시 만들기 횟수를 모두 썼어요. | `POST /api/stories/{id}/regenerate` (`regenerateRemaining` 0) |
| `PAGE_OUT_OF_RANGE` | 400 | 없는 쪽이에요. | `PUT /api/reading-sessions/{sessionId}/progress` (1~8 밖) |
| `STORY_BLOCKED` | 409 | 이 내용으로는 동화를 만들 수 없어요. | 차단된 동화의 변경 요청 전부(입력, 인터뷰, 확인, 다시 만들기, 확정 등) → ST-05 |
| `STORY_INPUT_INCOMPLETE` | 400 | 상황과 아이 정보를 먼저 입력해 주세요. | `POST /api/stories/{id}/interview/start` (상황이나 아이 정보 없음) |
| `AI_UNAVAILABLE` | 503 | 동화 도우미가 잠시 응답하지 않아요. 잠시 후 다시 시도해 주세요. | AI를 부르는 요청: 상황·아이·전하고 싶은 말 입력, 인터뷰 시작·답변, 확인, 다시 만들기, 다시 시도. 아무것도 저장되지 않았으니 같은 요청을 다시 보내게 안내 |
| `AI_BUSY` | 409 | 이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요. | 인터뷰 시작·답변, 확인, 다시 만들기, 다시 시도 (같은 동화에 요청이 겹침). 중복 클릭 방지 |
| `AI_STATE_MISMATCH` | 409 | 진행 상태가 맞지 않아요. 화면을 새로고침해 주세요. | 인터뷰 시작·답변, 확인, 다시 만들기, 다시 시도 (AI 쪽 진행 상태가 다름) → 상세·draft를 다시 불러 화면을 맞춘다 |
| `TONE_REQUIRED` | 400 | 이야기 분위기를 먼저 골라 주세요. | `POST /api/stories/{id}/confirm` (분위기 선택이 필요한데 안 고름) → ST-04 |
| `INVALID_PAGE_SELECTION` | 400 | 다시 그릴 쪽을 다시 골라 주세요. | `POST /api/stories/{id}/regenerate` (IMAGES가 아닌데 pages, 1~8 밖, 중복) |
| `FEEDBACK_TOO_LONG` | 400 | 요청 메모는 300자까지 쓸 수 있어요. | `POST /api/stories/{id}/regenerate` (메모 300자) |
| `VERSION_NOT_FOUND` | 404 | 이 동화의 버전을 찾을 수 없어요. | `preview`, `current-version`, `regenerate`, 그림 API (이 동화의 버전이 아님) |
| `IMAGE_NOT_FOUND` | 404 | 그림을 찾을 수 없어요. | 그림 API (그림이 실패한 쪽, 없는 쪽, 캐릭터 시트 없음) → 빈 자리 |
| `READING_SESSION_NOT_FOUND` | 404 | 읽기 기록을 찾을 수 없어요. 처음부터 다시 읽어 주세요. | `/api/reading-sessions/{sessionId}/...` (없거나 다른 회원의 세션) → 처음부터 읽기 |
| `PROMPT_NOT_FOUND` | 404 | 이 동화의 대화 질문을 찾을 수 없어요. | `PUT /api/reading-sessions/{sessionId}/dialogue/{promptId}` (이 세션 버전의 질문이 아님) |

## 오류가 아닌 특별한 응답

| 응답 | 뜻 |
|---|---|
| 204 `GET /api/stories/{id}/reading-sessions/resume` | 이어 읽을 세션이 없다 (오류 아님) |
| 200 + `safety.action: ASK_REVISE` | 저장하지 않았다. `safety.reason`을 보여 주고 다시 쓰게 한다 ([screens.md](screens.md)) |
| 200 + `safety.action: BLOCK`, `status: BLOCKED` | 차단. ST-05로 |
| `GET /generation`의 `status: FAILED`, `error` | 생성 실패. `error`는 내부 원인 코드(예: `TIMEOUT`, `OUTPUT_UNSAFE`)라 화면에는 ST-03 공통 문구를 쓴다 |
