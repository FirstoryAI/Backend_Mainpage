# TASK-07. 홈, 서재, 동화 상세, 대화 가이드, 함께 읽기

`CLAUDE.md`를 먼저 읽고 시작한다. 이 작업이 끝나면 HOME-01, LIB-01, LIB-02, GDE-01, RD-01~03 화면에 필요한 API가 모두 동작해야 한다. 이미 저장된 데이터를 보여주고 읽기 기록을 남기는 작업이라 AI 호출은 없다.

DB 마이그레이션은 없다 (V1~V5 그대로).

## 작업 순서

각 단계 끝에 `./gradlew build` 통과 후 커밋 (`&&`, 파이프 없이). Docker Desktop 확인. 마지막에 push.

0. TASK-06 TODO 두 개 정리
1. 서재 목록과 홈
2. 동화 상세와 삭제
3. 대화 가이드
4. 함께 읽기
5. 테스트, 문서 갱신

---

## 0. TASK-06 TODO 정리

### 0-1. 가이드 키 이름 확정

AI 저장소 `nodes/render.py`에서 확인한 실제 구조다. `TODO(AI 연동)`을 지우고 이 이름으로 고정한다.

```json
{
  "before_reading": { "intent": "…", "tips": ["…", "…"] },
  "prompts": [
    { "after_page": 3, "purpose": "…", "text": "질문 본문", "parent_hint": "…" }
  ],
  "after_reading": { "bridge_question": "…", "if_undecided": "…" }
}
```

- `prompts[].text` → DB `guide_prompts.question` (이미 그렇게 매핑돼 있으면 그대로)
- `seq`는 AI가 주지 않으므로 받은 순서대로 1부터 매긴다
- `parent_hint`, `if_undecided`는 없을 수 있다 (null 허용)

### 0-2. 결과 위기 신호(OUTPUT_UNSAFE) 후 다시 시도

진짜 AI는 결과가 위험하다고 판단돼도 그래프가 **부모 검토(parent_review)에서 멈춘 상태**다. 이때 `retry`(실패 재실행)를 부르면 409가 난다. 그래서:

- 마지막 실패 실행의 errorCode가 `OUTPUT_UNSAFE`면, 다시 시도는 `AiClient.retry` 대신 **`AiClient.regenerate`**를 부른다
  - scope: 실패한 실행의 scope. ALL(첫 생성)이었으면 CHARACTER_STORY
  - pages: 실패한 실행의 targetPages
  - feedback: null
  - baseCheckpointId: 실패한 실행의 baseVersion의 checkpointId. 첫 생성이라 baseVersion이 없으면 **null** (AI는 현재 상태에서 다시 만든다)
- 새 실행의 trigger는 그대로 RETRY. **다시 만들기 횟수에 넣지 않는다**
- 그 외 실패(AI 오류, TIMEOUT 등)는 지금처럼 `retry`
- `FakeAiClient`도 이 구분대로 동작하게 맞추고, `TODO(AI 연동)`을 지운다
- `docs/FIRSTORY-AI-API-SPEC-v0.3.md` 7-7에 다음 두 줄을 추가한다:
  - "`base_checkpoint_id`가 null이면 thread의 현재 상태에서 다시 만든다"
  - "결과 위기 신호로 실패 처리된 경우, 백엔드는 retry 대신 review regenerate를 부른다"

---

## 1. 서재 목록과 홈

### 서재 목록: `GET /api/stories` (LIB-01)

쿼리: `filter`(기본 ALL), `page`(0부터), `size`(기본 20, 최대 50)

| filter | 화면 탭 | 포함 상태 |
|---|---|---|
| ALL | 전체 | BLOCKED를 뺀 전부 |
| CREATING | 만드는 중 | DRAFT, INTERVIEWING, CONFIRMING, REQUESTED, GENERATING_TEXT, GENERATING_IMAGES |
| REVIEWING | 확인을 기다려요 | REVIEWING |
| UNREAD | 아직 안 읽었어요 | COMPLETED + firstReadCompletedAt 없음 |
| READ | 함께 읽었어요 | COMPLETED + firstReadCompletedAt 있음 |
| FAILED | 만들지 못했어요 | FAILED |

- BLOCKED 동화는 서재에 보이지 않는다
- 정렬: 최근 수정 순 (`updated_at DESC`, 같으면 id DESC)

응답:

```json
{
  "items": [
    {
      "id": 12,
      "title": "공룡 아로의 새 친구",
      "status": "COMPLETED",
      "label": "아직 안 읽었어요",
      "coverUrl": "/api/stories/12/versions/31/character-sheet",
      "childDisplayName": "아이 A",
      "createdAt": "…",
      "updatedAt": "…"
    }
  ],
  "page": 0, "size": 20, "totalElements": 7, "hasNext": false
}
```

- 버전이 아직 없으면 `title`은 null, `coverUrl`은 null (프론트가 "만들던 이야기" 표시)
- `label`은 `Story.libraryLabel()`
- **N+1 금지**: 현재 버전, 아이는 페치 조인 또는 한 번의 추가 조회로 가져온다. 목록 20개에 쿼리가 몇 개 나가는지 테스트에서 확인 (Hibernate Statistics 등으로, 목록 크기와 무관하게 일정해야 함)

### 홈: `GET /api/home` (HOME-01)

```json
{
  "onboardingSeen": true,
  "inProgress": [ /* 서재 카드와 같은 형식, CREATING·REVIEWING·FAILED 상태, 최근 3개 */ ],
  "recent": [ /* COMPLETED, 최근 확정 순 3개 */ ],
  "counts": { "creating": 1, "reviewing": 0, "unread": 2, "read": 4, "failed": 0 }
}
```

- 회원 정보(이메일, 휴대폰)는 넣지 않는다 (`/api/me`가 따로 있음)

## 2. 동화 상세와 삭제

### 상세: `GET /api/stories/{id}` (LIB-02)

```json
{
  "id": 12,
  "title": "공룡 아로의 새 친구",
  "status": "COMPLETED",
  "label": "함께 읽었어요",
  "coverUrl": "…",
  "childDisplayName": "아이 A",
  "situation": { "category": "FRIEND_CONFLICT", "categoryLabel": "친구와 다툼", "timing": "PAST" },
  "summary": "인터뷰 요약 문단",
  "pageCount": 8,
  "createdAt": "…",
  "confirmedAt": "…",
  "readCount": 2,
  "lastReadAt": "…",
  "canRead": true
}
```

- `readCount`: 완료된 읽기 세션 수, `lastReadAt`: 마지막 세션 시작 시각
- `canRead`: COMPLETED일 때만 true
- BLOCKED는 404처럼 다루지 말고 상태 그대로 보여준다 (차단 화면 재진입용)

### 삭제: `DELETE /api/stories/{id}` (LIB-02 "이 동화 지우기")

TASK-05의 삭제 API를 넓힌다.

| 상태 | 삭제 |
|---|---|
| DRAFT, INTERVIEWING, CONFIRMING, BLOCKED, REVIEWING, COMPLETED, FAILED | 가능 |
| REQUESTED, GENERATING_TEXT, GENERATING_IMAGES | `INVALID_STATUS_TRANSITION` (생성 중에는 지울 수 없음) |

- 소프트 삭제 후 목록·상세·그림 API에서 모두 404
- 입력 원문, 그림 파일의 실제 파기는 `// TODO(PM): PRD 36 파기 정책`. 지금은 소프트 삭제만

## 3. 대화 가이드: `GET /api/stories/{id}/guide` (GDE-01)

COMPLETED일 때만 (아니면 `INVALID_STATUS_TRANSITION`). 현재 버전의 가이드.

```json
{
  "storyId": 12,
  "title": "…",
  "beforeReading": { "intent": "…", "tips": ["…"] },
  "prompts": [
    { "id": 101, "seq": 1, "afterPage": 3, "question": "…", "purpose": "…", "parentHint": "…" }
  ],
  "afterReading": { "bridgeQuestion": "…", "ifUndecided": "…" }
}
```

## 4. 함께 읽기 (RD-01~03)

모두 로그인, 동화 소유자 확인(404), 동화가 COMPLETED여야 한다.

| # | 메서드 | 경로 | 화면 |
|---|---|---|---|
| 1 | GET | `/api/stories/{id}/book` | RD-01 읽기 화면 내용 |
| 2 | POST | `/api/stories/{id}/reading-sessions` | 읽기 시작 |
| 3 | GET | `/api/stories/{id}/reading-sessions/resume` | 이어 읽기 |
| 4 | PUT | `/api/reading-sessions/{sessionId}/progress` | 쪽 넘김 |
| 5 | PUT | `/api/reading-sessions/{sessionId}/dialogue/{promptId}` | RD-02 대화 버튼 |
| 6 | POST | `/api/reading-sessions/{sessionId}/complete` | RD-03 끝 |

### 1. book

현재(확정된) 버전의 표지와 8쪽, 그리고 쪽 사이에 나올 대화 질문.

```json
{
  "storyId": 12,
  "versionId": 31,
  "title": "…",
  "coverUrl": "…",
  "pageCount": 8,
  "pages": [ { "pageNo": 1, "text": "…", "imageUrl": "…", "imageStatus": "DONE" } ],
  "prompts": [ { "id": 101, "afterPage": 3, "question": "…", "parentHint": "…" } ]
}
```

### 2. reading-sessions 시작

`ReadingSession.start(story, currentVersion, now)` → `201 {sessionId, lastPage: 0}`

### 3. resume

가장 최근의 **완료되지 않은** 세션. 없으면 `204`. 응답 `{sessionId, lastPage, startedAt}`.

### 4. progress

`{page}` → `session.moveTo(page, pageCount)`. 범위 밖이면 `PAGE_OUT_OF_RANGE`. 완료된 세션이면 `INVALID_STATUS_TRANSITION`. 응답 `204`.

### 5. dialogue

`{action: "TALKED" | "SKIPPED"}`

- 질문이 이 세션의 버전 가이드에 속하지 않으면 404
- **같은 세션, 같은 질문에 다시 누르면 마지막 값으로 바꾼다** (부모가 잘못 눌렀다 고칠 수 있게). `DialogueAction`에 `change(action)` 추가
- 응답 `204`
- North Star(대화 발생)는 TALKED 수로 센다

### 6. complete

`session.complete(now)` + `story.markFirstReadCompleted(now)` (처음 한 번만 기록됨) → `204`. 이미 완료된 세션이면 그대로 `204` (두 번 눌러도 안전).

### 세션 소유자 확인

세션 id로 들어오는 API(4~6)는 **세션 → 동화 → 회원**까지 확인한다. 다른 회원의 세션이면 404.

---

## 5. 테스트

### 통합 테스트 (MockMvc + Testcontainers + FakeAiClient)

테스트용 도우미로 "가입 → 동화 만들기 → 생성 → 확정"을 한 번에 해 주는 픽스처를 만든다 (기존 도우미가 있으면 재사용).

| 시나리오 | 확인할 것 |
|---|---|
| 0-1 가이드 키 | AI 결과 형식 그대로 저장되고 `/guide`에 나옴, parent_hint 없어도 저장 |
| 0-2 결과 위기 신호 재시도 | `[[OUTPUT_REVISE]]` → FAILED → retry → AI `regenerate` 호출(첫 생성이면 base null, scope CHARACTER_STORY) → 성공, 다시 만들기 횟수 그대로 |
| 서재 필터 | 상태가 다른 동화 6개를 만들어 filter별 개수와 label이 맞음 / BLOCKED는 어디에도 없음 |
| 서재 페이지 | size 2로 나눠 받기, hasNext |
| 서재 N+1 | 동화 3개와 20개일 때 쿼리 수가 같음 |
| 홈 | inProgress 최대 3, recent 최대 3, counts |
| 상세 | readCount, lastReadAt, canRead / 다른 회원 404 |
| 삭제 | COMPLETED 삭제 후 목록·상세·그림 404 / 생성 중 삭제는 409 |
| 가이드 | COMPLETED만, 질문 순서 |
| 읽기 흐름 | book → 시작 → progress 1~8 → dialogue TALKED → complete → 서재 label "함께 읽었어요", readCount 1 |
| 이어 읽기 | 쪽 5에서 멈춘 세션 → resume가 그 세션과 lastPage 5 / 완료 후 resume 204 |
| 대화 버튼 바꾸기 | SKIPPED 후 TALKED → 행 1개, TALKED |
| 남의 질문 | 다른 동화의 promptId → 404 |
| 세션 소유자 | 다른 회원의 sessionId로 progress·dialogue·complete → 404 |
| 확정 전 읽기 | REVIEWING 동화로 book·시작 → 409 |
| 두 번 완료 | complete 두 번 → 둘 다 204, firstReadCompletedAt 처음 값 유지 |

### 문서 갱신

- `CLAUDE.md`: 서재 필터 규칙, BLOCKED는 서재에서 숨김, 삭제 가능 상태, 읽기 규칙, 작업 문서 목록에 TASK-07
- Swagger: 새 API에 한국어 요약과 오류 코드

---

## 완료 조건

- [ ] `./gradlew build` 통과 (기존 테스트 포함 전부)
- [ ] DB 마이그레이션 추가 없음
- [ ] TASK-06의 `TODO(AI 연동)` 두 개가 정리됐다
- [ ] 서재 목록 쿼리 수가 목록 크기와 무관하다
- [ ] 세션·질문·버전을 id로 받는 모든 API가 회원까지 거슬러 소유자를 확인한다
- [ ] local 프로필에서 Swagger로 생성 → 확정 → 서재 → 상세 → 가이드 → 읽기 → 완료까지 해볼 수 있다
- [ ] 커밋은 빌드가 통과한 상태로만 (`&&`, 파이프 없이)
