# 화면별 API 순서

와이어프레임 화면 번호 기준이다. 요청·응답의 전체 필드는 Swagger(http://localhost:8081/swagger-ui/index.html)에 있고, 여기에는 **언제 무엇을 부르고 응답에서 무엇을 보면 되는지**만 적는다.

- 경로의 `{id}`는 동화 id(`storyId`), `{sessionId}`는 읽기 세션 id다.
- 상태를 바꾸는 요청에는 `X-XSRF-TOKEN` 헤더가 필요하다 ([auth.md](auth.md)).
- 오류 응답은 항상 `{"code": "...", "message": "..."}`. `message`는 사용자에게 그대로 보여 줘도 되는 문장이다 ([errors.md](errors.md)).
- 시각은 모두 UTC ISO-8601(`2026-10-23T02:15:00Z`). 화면에는 한국 시간으로 바꿔 보여 준다.
- AUTH 화면 번호가 와이어프레임과 다르면 화면 이름을 기준으로 본다.

## 동화 상태 (`status`)

| status | 뜻 | 보통 가는 화면 |
|---|---|---|
| `DRAFT` | 입력 중 (CRT-01~04) | CRT-01~04 (`GET /draft`로 이어서) |
| `INTERVIEWING` | 인터뷰 중 | CRT-05 |
| `CONFIRMING` | 인터뷰 끝, 만들기 전 확인 | CRT-06 |
| `REQUESTED`, `GENERATING_TEXT`, `GENERATING_IMAGES` | 만드는 중 | GEN-01 |
| `REVIEWING` | 다 만들어져 부모 확인 대기 | REV-01 |
| `COMPLETED` | 확정됨 | LIB-02, GDE-01, RD-01 |
| `FAILED` | 만들지 못함 | ST-03 |
| `BLOCKED` | 위기 신호로 차단 | ST-05 |

서재 카드와 상세의 `label`("만들던 이야기", "확인을 기다려요", "아직 안 읽었어요" 등)은 서버가 정한 문구라 그대로 보여 준다.

---

## LND-01 랜딩

부르는 API 없음. (앱 공통으로 처음 열 때 `GET /api/auth/csrf`, 로그인 여부는 `GET /api/me` 200/401로 판단해 HOME-01로 바로 보낼 수 있다.)

## AUTH-01 로그인

| 때 | API | 볼 것 |
|---|---|---|
| 들어올 때 | `GET /api/auth/csrf` (앱 시작 시 한 번 했으면 생략) | 204 |
| 로그인 버튼 | `POST /api/auth/login` `{email, password, rememberMe}` | 200 `{id, email, phoneMasked, onboardingSeen, smsNotify}` |
| 다음 화면 | `onboardingSeen`이 false면 ONB-01, 아니면 HOME-01 | |

오류: 401 `INVALID_CREDENTIALS`(입력란 아래 메시지), 423 `ACCOUNT_LOCKED`.

## AUTH-02 가입 정보·약관 동의

| 때 | API | 볼 것 |
|---|---|---|
| 약관 항목 | (API 없음) `consents` 키: `GUARDIAN`, `TERMS`, `PRIVACY`(필수), `SMS_NOTIFY`(선택) | |
| 가입 버튼 (AUTH-03 인증을 마친 뒤) | `POST /api/auth/signup` `{email, password, phone, consents: {GUARDIAN: true, ...}, termsVersion: "2026-10-01"}` | 201 MeResponse. **바로 로그인된 상태** |
| 다음 화면 | ONB-01 | |

오류: 409 `DUPLICATE_EMAIL`, `DUPLICATE_PHONE`, 400 `INVALID_PASSWORD_FORMAT`, `REQUIRED_CONSENT_MISSING`, `PHONE_NOT_VERIFIED`(인증 안 했거나 10분 지남).

## AUTH-03 휴대폰 인증

| 때 | API | 볼 것 |
|---|---|---|
| 인증번호 받기 | `POST /api/auth/phone/send-code` `{phone: "010-1234-5678", purpose: "SIGNUP"}` | 200 `{expiresInSeconds: 180}` → 3분 타이머 |
| 확인 | `POST /api/auth/phone/verify` `{phone, code}` | 204 → 인증 완료 표시 |

- local 인증번호는 `000000`.
- 다시 받기는 60초 뒤부터(429 `SMS_RESEND_TOO_SOON`), 1시간에 5번(429 `SMS_RATE_LIMITED`).
- 틀리면 400 `VERIFICATION_CODE_MISMATCH`, 5번 틀리면 429 `VERIFICATION_ATTEMPTS_EXCEEDED`(다시 받기), 만료면 400 `VERIFICATION_EXPIRED`.
- 이미 가입된 번호면 send-code에서 409 `DUPLICATE_PHONE`.

## ONB-01 첫 방문 안내

| 때 | API |
|---|---|
| 닫기/시작하기 | `POST /api/me/onboarding-seen` → 204 |
| 다음 화면 | HOME-01 |

## HOME-01 홈

| 때 | API | 볼 것 |
|---|---|---|
| 들어올 때 | `GET /api/home` | `inProgress`(만드는 중·확인 대기·실패 최근 3개), `recent`(최근 완성 3개), `counts{creating, reviewing, unread, read, failed}`, `onboardingSeen` |
| 카드 누름 | 카드 `status`로 화면을 고른다(위 표). 완성된 동화는 LIB-02 | |
| 새 동화 만들기 | CRT-01로 | |

카드(`StoryCardResponse`): `id, title, status, label, categoryLabel, coverUrl, childDisplayName, createdAt, updatedAt`. `categoryLabel`은 상황 분류 문구("기타"를 골랐으면 직접 입력한 분류)이고, 상황을 아직 입력하지 않았으면 null. `title`, `coverUrl`은 그림이 나오기 전엔 null. **차단된 동화(BLOCKED)는 홈과 서재에 나오지 않는다.**

---

## 동화 만들기 (CRT-01 ~ CRT-06)

### 위기 신호 분기 (모든 만들기 응답 공통)

글을 받는 API(상황, 아이, 전하고 싶은 말, 나누고 싶은 것, 인터뷰 답변, 다시 만들기 메모)의 응답에는 `safety`가 있다.

```json
{ "storyId": 12, "status": "DRAFT", "saved": true, "toneRequired": false,
  "safety": { "action": "PROCEED", "level": "OK", "category": null, "reason": null, "helpKind": null } }
```

| `safety.action` | 저장됨? | 프론트 처리 |
|---|---|---|
| `PROCEED` | 예 | 다음 단계로 |
| `CHOOSE_TONE` | 예 | **ST-04 분위기 선택**을 띄운다. 고르면 `PUT /tone` 후 원래 다음 단계로. `toneRequired`가 true가 된다 |
| `ASK_REVISE` | **아니오** | `safety.reason`을 안내로 보여 주고 **같은 화면에서 다시 쓰게** 한다. 인터뷰면 같은 질문이 다시 온다 |
| `BLOCK` | **아니오** | **ST-05 차단 화면**. `status`가 `BLOCKED`. `safety.helpKind`에 맞는 상담 기관을 프론트가 보여 준다 |

- `helpKind`: `CHILD_SAFETY`(아이 안전이 걱정됨) / `PARENTING`(양육 상담). **상담 기관 이름과 번호는 백엔드에 없다.** 프론트가 helpKind별로 정해서 보여 준다(문구 PM 확정 전).
- 차단된 동화에 다시 요청하면 409 `STORY_BLOCKED`. 나가기(`DELETE /api/stories/{id}`)와 조회만 된다.
- `ASK_REVISE`가 쌓이면(같은 동화에서 2번째) `BLOCK`이 된다.
- 로컬에서 흉내 내려면 글에 `[[SENSITIVE]]`, `[[REVISE]]`, `[[CRISIS]]`를 넣는다 ([fake-ai.md](fake-ai.md)).

### CRT-01 · CRT-02 상황

| 때 | API | 볼 것 |
|---|---|---|
| "새 동화 만들기" | `POST /api/stories` | 201 `{storyId, status: "DRAFT"}` → 이후 모든 경로에 storyId |
| 다음 버튼 | `PUT /api/stories/{id}/situation` | `saved`, `safety.action` |

```json
{ "timing": "PAST", "category": "NEW_ENVIRONMENT", "customCategory": null,
  "description": "새 어린이집에 갔어요", "reactions": ["AVOIDED"], "customReaction": null, "emotions": ["SCARED"] }
```

- `timing`: `PAST`(있었던 일) / `UPCOMING`(앞으로 겪을 일). **UPCOMING이면 `reactions`, `emotions`를 보내지 않는다**(400 `MOMENT_ONLY_FOR_PAST`).
- `category`: `FRIEND_CONFLICT`, `NEW_ENVIRONMENT`, `SEPARATION`, `SAFETY`, `RULES`, `FAMILY_CHANGE`, `FEAR`, `OTHER`
- `reactions`: `CRIED`, `SILENT`, `ANGRY_OUTBURST`, `CLINGY`, `AVOIDED`, `ASKED_QUESTIONS`, `OTHER`
- `emotions`: `SAD`, `ANGRY`, `SCARED`, `CONFUSED`, `ASHAMED`, `EXCITED`, `UNKNOWN`
- `OTHER`를 고르면 `customCategory`/`customReaction` 필수(30자). `description`은 60자.
- 상황·반응·감정의 화면 문구는 아직 선택지 API가 없다([status.md](status.md)). 위 코드에 맞춰 임시 문구를 쓴다.

### CRT-03 아이 정보

| 때 | API | 볼 것 |
|---|---|---|
| 들어올 때 | `GET /api/children` (저장된 아이 목록), `GET /api/children/options` (성향·성별·좋아하는 것 버튼 문구와 제한) | |
| 다음 버튼 | `PUT /api/stories/{id}/child` | `saved`, `safety.action` |

```json
{ "childId": 3, "saveForNextTime": true,
  "profile": { "birthYm": "2021-03", "gender": "FEMALE", "traits": ["SHY"], "customTrait": null,
               "interestCodes": ["DINOSAUR"], "customInterests": ["기차"] } }
```

- 저장된 아이를 불러왔으면 `childId`, 새로 입력이면 `null`. `profile`은 **항상 화면의 최종 입력값**을 보낸다.
- "다음에도 이 아이 정보로 만들기" 체크 = `saveForNextTime`. 체크하면 새 아이는 저장(호칭 "아이 A" 자동), 불러온 아이는 입력값으로 갱신.
- 좋아하는 것은 `interestCodes` + `customInterests` 합쳐 5개까지(400 `INTERESTS_LIMIT_EXCEEDED`).

### CRT-04 전하고 싶은 말

| 때 | API | 볼 것 |
|---|---|---|
| 다음 버튼 | `PUT /api/stories/{id}/message` `{message}` | `saved`, `safety.action` |
| "잘 모르겠어요" | `{message: null}` 또는 빈 문자열 | |

### ST-04 이야기 분위기 선택

`safety.action`이 `CHOOSE_TONE`일 때 띄운다.

| 때 | API |
|---|---|
| 고르기 | `PUT /api/stories/{id}/tone` `{tone: "WARM"}` (`PLAIN`, `WARM`, `UNDECIDED`(정하지 못함) 중 하나) → 200 (safety는 null) |

분위기를 고르지 않고 CRT-06에서 확인하면 400 `TONE_REQUIRED`. `GET /draft`의 `toneRequired`, `tone`으로 다시 확인할 수 있다.

### CRT-05 인터뷰

| 때 | API | 볼 것 |
|---|---|---|
| 들어올 때 | `POST /api/stories/{id}/interview/start` | 답변과 같은 `InterviewResponse`. 보통 `type: "QUESTION"`, `status: "INTERVIEWING"`, `turn: 1`, `question`. **바로 `type: "CONFIRM"`일 수 있다**(아래) |
| 답변 | `POST /api/stories/{id}/interview/answer` `{answer: "..."}` (500자) | 아래 |
| 건너뛰기 | `POST /api/stories/{id}/interview/answer` `{skip: true}` | 인터뷰가 끝난다 |

답변 응답(`InterviewResponse`): `status, type, turn, question, choices, summary, safety, toneRequired`

- `type: "QUESTION"` → `question`을 다음 질문으로 보여 준다. `turn`이 질문 번호.
- `choices`(문자열 0~4개) → 질문 아래 답변 카드. 누르면 그 문장을 `{answer: "..."}`로 보낸다(직접 쓴 답과 똑같이 위기 신호를 검사한다). 빈 목록이면 카드 없이 입력만 보여 준다. 질문이 아닌 응답(CONFIRM, 차단)은 항상 빈 목록이다.
- 카드는 저장하지 않는다. `ASK_REVISE`로 같은 질문을 다시 줄 때와 `GET /draft`로 이어서 만들 때는 카드가 없다.
- `type: "CONFIRM"` → 인터뷰 끝. `status`가 `CONFIRMING`, `summary`("이번 이야기에서 나누고 싶은 것", 부모에게 보여 줄 해요체 문장) → CRT-06.
- **질문 수는 0~5개 사이에서 동화마다 다르다.** "3/5" 같은 고정 진행 표시를 하지 않는다.
- **질문이 0개일 수 있다.** AI가 앞서 받은 입력만으로 충분하다고 보면 인터뷰 시작 응답이 바로 `type: "CONFIRM"`, `status: "CONFIRMING"`, `summary`다. 이때는 채팅 화면을 열지 않고 CRT-06으로 간다. 가짜 AI(local, dev)는 항상 질문 1부터 시작하므로 진짜 AI를 붙였을 때만 나온다.
- 응답이 수 초 걸린다(가짜 AI도 2초). 로딩 표시와 **중복 클릭 방지** 필수. 두 번 보내면 409 `AI_BUSY`.
- `ASK_REVISE`면 `question`에 같은 질문이 다시 온다. `CHOOSE_TONE`이면 진행하면서 `toneRequired: true`(CRT-06 전에 ST-04).
- AI 오류(503 `AI_UNAVAILABLE`, 409 `AI_BUSY`)면 아무것도 저장되지 않았으니 같은 답변으로 다시 보내면 된다.

### CRT-06 만들기 전 확인

| 때 | API | 볼 것 |
|---|---|---|
| 들어올 때 / 이어서 만들기 | `GET /api/stories/{id}/draft` | `situation`, `child`, `parent.message`, `interview.turns[]{turnNo, question, answer}`, `interview.summary`, `tone`, `toneRequired` |
| 아이 수정하기 | `PUT /api/stories/{id}/child` (CRT-03과 같은 본문) | `{saved, status: "CONFIRMING", safety, toneRequired}` |
| 상황 수정하기 | `PUT /api/stories/{id}/situation` (CRT-01·02와 같은 본문) | 같음 |
| 전하고 싶은 말 수정하기 | `PUT /api/stories/{id}/message` | 같음. 인터뷰가 채운 다른 값(방향 등)은 그대로 |
| 나누고 싶은 것 수정하기 | `PUT /api/stories/{id}/summary` `{summary}` (1~300자) | 같음. `GET /draft`의 `interview.summary`가 바뀐다 |
| 동화 만들기 버튼 | `POST /api/stories/{id}/confirm` | 202 `{status: "REQUESTED", runNo, stage, ...}` → GEN-01 |

- 수정은 `CONFIRMING`에서 된다(아이·상황·전하고 싶은 말은 `DRAFT`에서도). 인터뷰를 다시 하지 않고, 고친 내용은 "동화 만들기"를 누를 때 AI에 함께 간다. **지우고 다시 만들어 답변을 재생하는 우회는 필요 없다.**
- 고친 글도 위기 신호를 검사한다. 응답의 `safety`는 위 "위기 신호 분기" 표대로 처리한다(`ASK_REVISE`면 저장되지 않으니 같은 카드에서 다시 쓰게, `CHOOSE_TONE`이면 만들기 전에 ST-04).

`GET /draft`는 모든 상태에서 되므로 "만들던 이야기" 카드에서 이어서 만들 때도 쓴다. `status`를 보고 CRT-01~06 중 어디로 갈지 정한다.

---

## GEN-01 만드는 중

| 때 | API | 볼 것 |
|---|---|---|
| 들어와서 **3초마다** | `GET /api/stories/{id}/generation` | `status`, `stage`(1~4), `stageLabel`, `startedAt`, `error` |

- `stage`: 1 이야기 짜기, 2 글 쓰기, 3 그림 그리기, 4 책으로 묶기. `stageLabel`을 그대로 보여 줘도 된다.
- 이 API는 DB만 읽어서 3초마다 불러도 된다. 진행은 서버가 뒤에서 AI에 묻는다.
- **화면을 떠나도 생성은 계속된다.** "다 되면 알려 드릴게요, 나가셔도 돼요" 안내. 나중에 홈·서재 카드(`REVIEWING`)로 돌아온다. 문자 알림 동의(`smsNotify`)를 했으면 완성/실패 문자가 간다(local은 로그만).
- `status`가 `REVIEWING` → REV-01. `FAILED` → ST-03. 폴링을 멈춘다.
- 진짜 AI는 3~4분, 가짜 AI(local)는 약 2분 40초.

## ST-03 만들지 못했어요

| 때 | API | 볼 것 |
|---|---|---|
| 다시 시도 | `POST /api/stories/{id}/retry` | 202 → GEN-01 (폴링 다시) |
| 나가기 | `DELETE /api/stories/{id}` → 204 | |

다시 시도는 다시 만들기 횟수에 들어가지 않는다. 로컬에서는 전하고 싶은 말에 `[[FAIL]]`을 넣으면 첫 생성이 실패한다.

---

## REV-01 미리보기

| 때 | API | 볼 것 |
|---|---|---|
| 들어올 때 | `GET /api/stories/{id}/preview` | `version{id, versionNo, title}`, `characterSheetUrl`, `pages[]{pageNo, text, imageStatus, imageUrl, textPosition}`, `regenerateRemaining` |
| 다시 만들기 | REV-02 | |
| 동화 설명과 부모용 가이드 | GDE-01 (`GET .../guide`, REVIEWING에서도 된다) | 동화 설명은 `beforeReading.intent` |
| 버전 목록 | 아래 "버전 목록" | |
| 이대로 확정 | REV-04 | |

**그림**

- `imageUrl`(예: `/api/stories/12/versions/30/pages/1/image`)을 **`<img src>`에 그대로** 넣는다. 쿠키가 자동으로 붙어 내 동화 그림만 보인다. 주소에 파일 경로는 없다.
- `imageStatus: "FAILED"`면 `imageUrl`이 null이다. **빈 자리(대체 그림)** 를 보여 주고 "그림만 다시"로 그 쪽을 다시 그리게 안내한다.
- `characterSheetUrl`은 표지·인물 그림. null일 수 있다.
- `textPosition`(`TOP` | `BOTTOM`)은 쪽 글을 그림 위에 얹을 자리다. 그림은 그쪽 약 30%를 빈 바닥·벽·하늘로 비워 두므로, 글 상자를 그 안에 가로로 넓게 놓으면 인물을 가리지 않는다. 빈 자리 경계가 딱 잘리지 않아 반투명 배경을 깔면 안전하다. 예전에 만든 동화는 모두 `BOTTOM`이다.
- 쪽 글의 `**콩닥콩닥**`은 의성어·의태어 강조 표시다. 굵게 등으로 바꿔 보여 준다.

## REV-02 다시 만들기

| 때 | API | 볼 것 |
|---|---|---|
| 다시 만들기 버튼 | `POST /api/stories/{id}/regenerate` | 202 `{status: "REQUESTED", saved, safety}` → GEN-01 |

```json
{ "scope": "IMAGES", "pages": [3, 5], "feedback": "3쪽 공룡을 더 웃게 해 주세요" }
```

- `scope` 3가지: `CHARACTER_STORY`(이야기·인물부터 다시), `TEXT`(글 다시), `IMAGES`(그림만 다시).
- `IMAGES`일 때만 `pages`로 쪽을 고른다(1~10, 비우면 전체). 다른 범위에서 `pages`를 보내면 400 `INVALID_PAGE_SELECTION`.
- `feedback`(요청 메모)은 300자. 위기 신호를 먼저 검사한다: `ASK_REVISE`면 `saved: false`이고 다시 만들지 않으며 횟수도 그대로(메모를 고치게 안내). `BLOCK`이면 ST-05.
- `regenerateRemaining`(남은 횟수, 처음 1번)이 0이면 버튼을 막는다. 넘기면 409 `REGENERATION_LIMIT_REACHED`.
- 다시 만들면 **지금 보고 있는 버전**에서 갈라진다.

## 버전 목록

| 때 | API | 볼 것 |
|---|---|---|
| 목록 | `GET /api/stories/{id}/versions` | `[{id, versionNo, parentVersionNo, title, scope, createdAt, current}]` |
| 이 버전 보기 | `PUT /api/stories/{id}/current-version` `{versionId}` | 200 PreviewResponse (바꾼 버전의 미리보기) |

버전 전환은 다시 만들기 횟수에 들어가지 않는다. `current: true`가 지금 보여 주는 버전이고, 확정하면 이 버전이 책이 된다.

## REV-04 확정

| 때 | API | 볼 것 |
|---|---|---|
| 이대로 확정 | `POST /api/stories/{id}/approve` | 200 `{storyId, status: "COMPLETED", confirmedAt}` → LIB-02 또는 GDE-01 |

확정 후에는 다시 만들 수 없다.

---

## LIB-01 서재

| 때 | API | 볼 것 |
|---|---|---|
| 들어올 때 / 탭 | `GET /api/stories?filter=ALL&page=0&size=20` | `items[]`(카드), `page`, `size`, `totalElements`, `hasNext` |
| 더 보기 | `page`를 1씩 올린다(`hasNext`가 false면 끝) | |

`filter`: `ALL`(기본), `CREATING`(만드는 중), `REVIEWING`(확인을 기다려요), `UNREAD`(아직 안 읽었어요), `READ`(함께 읽었어요), `FAILED`(만들지 못했어요). 탭 개수는 `GET /api/home`의 `counts`. 최근 수정 순.

## LIB-02 동화 상세

| 때 | API | 볼 것 |
|---|---|---|
| 들어올 때 | `GET /api/stories/{id}` | `title, status, label, coverUrl, childDisplayName, situation{category, categoryLabel, timing}, summary, pageCount, createdAt, confirmedAt, readCount, lastReadAt, canRead` |
| 함께 읽기 | `canRead`가 true일 때 RD-01 | |
| 대화 가이드 | GDE-01 (REVIEWING·COMPLETED) | |
| 이 동화 지우기 | `DELETE /api/stories/{id}` → 204 → 서재로 | |

- 지우기는 만드는 중(`REQUESTED`, `GENERATING_TEXT`, `GENERATING_IMAGES`)에는 안 된다(409 `INVALID_STATUS_TRANSITION`).
- 지운 동화는 모든 API에서 404 `STORY_NOT_FOUND`.
- BLOCKED 동화도 상세는 열린다(ST-05 재진입용).

## GDE-01 대화 가이드

| 때 | API | 볼 것 |
|---|---|---|
| 들어올 때 | `GET /api/stories/{id}/guide` | `beforeReading{intent, tips[]}`, `prompts[]{id, seq, afterPage, question, purpose, parentHint}`, `afterReading{bridgeQuestion, ifUndecided}` |

`parentHint`, `ifUndecided`는 null일 수 있다. 미리보기(REVIEWING)와 확정 뒤(COMPLETED)에 지금 보여 주는 버전의 가이드를 준다. 그 밖의 상태면 409 `INVALID_STATUS_TRANSITION`. 함께 읽기(RD-01~03)는 확정 뒤에만 된다.

## RD-01 함께 읽기

| 때 | API | 볼 것 |
|---|---|---|
| 들어올 때 | `GET /api/stories/{id}/book` | `title, coverUrl, pageCount, pages[]{pageNo, text, imageUrl, imageStatus, textPosition}, prompts[]{id, afterPage, question, parentHint}` |
| 이어 읽기 확인 | `GET /api/stories/{id}/reading-sessions/resume` | 200 `{sessionId, lastPage, startedAt}`이면 "이어 읽을까요?", **204면 이어 읽을 것 없음** |
| 처음부터 읽기 | `POST /api/stories/{id}/reading-sessions` | 201 `{sessionId, lastPage: 0}` |
| 쪽 넘길 때마다 | `PUT /api/reading-sessions/{sessionId}/progress` `{page}` (1~10) | 204 |

`prompts[].afterPage`가 n이면 n쪽을 읽은 뒤 RD-02 대화 질문을 띄운다. 쪽 글은 미리보기처럼 `textPosition` 자리에 얹는다.

## RD-02 대화 질문

| 때 | API |
|---|---|
| 이야기 나눴어요 | `PUT /api/reading-sessions/{sessionId}/dialogue/{promptId}` `{action: "TALKED"}` → 204 |
| 건너뛰기 | 같은 API `{action: "SKIPPED"}` → 204 |

같은 질문에 다시 누르면 마지막 값으로 바뀐다. `promptId`는 `book`의 `prompts[].id`.

## RD-03 다 읽었어요

| 때 | API |
|---|---|
| 마지막 쪽 뒤 | `POST /api/reading-sessions/{sessionId}/complete` → 204 (두 번 불러도 204) |
| 다음 | GDE-01의 `afterReading`(읽은 뒤 질문)이나 서재로. 이 동화는 서재 `READ`(함께 읽었어요)로 옮겨진다 |

끝난 세션에 쪽 넘김을 보내면 409 `INVALID_STATUS_TRANSITION`. 다른 회원의 세션은 404 `READING_SESSION_NOT_FOUND`.

---

## MY-01 내 정보

| 때 | API | 볼 것 |
|---|---|---|
| 들어올 때 | `GET /api/me`, `GET /api/children` | `email`, `phoneMasked`, `smsNotify` / 아이 목록 |
| 문자 알림 켜기·끄기 | `PUT /api/me/consents/sms-notify` `{agreed: true}` | 204 |
| 아이 추가 | `POST /api/children` (CRT-03의 `profile`과 같은 본문) | 201 |
| 아이 정보 수정 | `PUT /api/children/{childId}` | 200 |
| 호칭 변경 | `PATCH /api/children/{childId}/display-name` `{displayName}` (1~30자) | 200 |
| 아이 삭제 | `DELETE /api/children/{childId}` | 204 (그 아이로 만든 동화는 남는다) |
| 비밀번호 변경 | `POST /api/me/password` `{currentPassword, newPassword}` | 204 |
| 휴대폰 변경 | `send-code`(`purpose: "CHANGE_PHONE"`) → `verify` → `POST /api/me/phone` `{currentPassword, phone}` | 204 |
| 로그아웃 | `POST /api/auth/logout` → 204 → `GET /api/auth/csrf` → LND-01 | |
| 탈퇴 | `POST /api/me/withdraw` `{currentPassword}` → 204 → `GET /api/auth/csrf` → LND-01 | |

아이 호칭(`displayName`)은 부모에게만 보이고 AI로 보내지 않는다. 다른 회원의 아이 id는 404 `CHILD_NOT_FOUND`.
