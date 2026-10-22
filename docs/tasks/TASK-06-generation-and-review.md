# TASK-06. 생성, 검토, 다시 만들기, 버전, 확정

`CLAUDE.md`를 먼저 읽고 시작한다. 이 작업이 끝나면 생성 전 확인(CRT-06)부터 확정(REV-04)까지 API로 동작해야 한다. AI 서버는 여전히 없으므로 **가짜 AI(`FakeAiClient`)가 8쪽짜리 동화와 임시 그림 파일을 실제로 만들어** 프론트가 미리보기, 그림, 다시 만들기, 버전 전환까지 붙여볼 수 있게 한다.

AI 호출 형식은 `docs/FIRSTORY-AI-API-SPEC-v0.3.md` 7-4 ~ 7-8을 따른다.

## 범위

**포함**: V5, 파일 저장소, AI 클라이언트 생성 메서드, 생성 진행 확인 스케줄러, 결과 저장, 그림 내려주기 API, 생성·검토 API 9개, 알림 기록(문자는 로그로만), 테스트

**제외**: 서재, 동화 상세, 대화 가이드 화면, 함께 읽기(TASK-07). 실제 문자 발송과 S3(이후)

## 작업 순서

각 단계 끝에 `./gradlew build` 통과 후 커밋 (`&&`, 파이프 없이). Docker Desktop 확인. 마지막에 push.

1. V5, 설정, 파일 저장소
2. AI 클라이언트 생성 메서드 (Http, Fake)
3. 생성 시작과 진행 확인 스케줄러
4. 결과 저장 (버전, 페이지, 가이드)
5. 검토 API (미리보기, 다시 만들기, 버전, 확정, 다시 시도)
6. 그림 내려주기 API
7. 알림 기록
8. 테스트, 문서 갱신

---

## 1. V5, 설정, 파일 저장소

### V5

`src/main/resources/db/migration/V5__run_target_pages.sql`은 **이미 제공되어 있다. 내용을 바꾸지 않는다.**
- `generation_runs.target_pages` (jsonb, 기본 `[]`): 그림만 다시 그릴 쪽 번호
- 실행 중인 작업을 찾는 부분 인덱스

`GenerationRun`에 `targetPages`(List<Integer>) 추가.

### 설정

```yaml
firstory:
  generation:
    poll-enabled: true          # test 프로필은 false (테스트에서 직접 호출)
    poll-interval: 5s
    timeout: 15m                # 이 시간이 지나도 RUNNING이면 실패 처리
  storage:
    local-root: ${STORAGE_LOCAL_ROOT:./data/storage}
```

`.gitignore`에 `data/` 추가.

### 파일 저장소 (`global.storage`)

```java
public interface FileStorage {
    StoredFile read(String key);          // 없으면 Optional.empty 또는 예외
    void write(String key, byte[] bytes, String contentType);   // 가짜 AI와 테스트용
    boolean exists(String key);
}
```

- 이번 작업은 `LocalFileStorage`만. S3는 나중에 같은 인터페이스로 추가한다
- **경로 보안**: `key`를 루트 아래 경로로 바꾼 뒤 `normalize()`하고, 결과가 루트 밖이면 예외. `..`, 절대 경로, 역슬래시 포함 key는 거부
- key 형식: `stories/{storyId}/runs/{runNo}/images/page-01.png`, `.../images/sheet-{role}-{n}.png`

## 2. AI 클라이언트 생성 메서드

`AiClient`에 추가 (스펙 v0.3 그대로):

```java
void generate(String threadId, UUID runId, int runNo, String outputPrefix, Tone tone);
AiGenerationStatus status(String threadId);
AiGenerationResult result(String threadId);
void approve(String threadId);
void regenerate(String threadId, RegenerateScope scope, List<Integer> pages, String feedback,
                String baseCheckpointId, UUID runId, int runNo, String outputPrefix);
void retry(String threadId, UUID runId, int runNo, String outputPrefix);
```

- `AiGenerationStatus(state, runNo, currentNode, stage, error)`: state는 RUNNING, REVIEWING, COMPLETED, FAILED 등
- `AiGenerationResult(runNo, checkpointId, title, design, characterSheets, pages, guide, cost, safety)`
- scope 변환: CHARACTER_STORY → `story`, TEXT → `text`, IMAGES → `images`
- `outputPrefix` = `stories/{storyId}/runs/{runNo}/`

### FakeAiClient 확장

진짜처럼 동작하되 예측할 수 있게 만든다.

- `generate`/`regenerate`/`retry`: thread 상태를 RUNNING, 단계 0으로
- `status`: **호출될 때마다 한 단계씩 진행**: `design_story`(1) → `write_story`(2) → `illustrate`(3) → `render`(4) → REVIEWING
  - 그림만 다시는 `illustrate_only`(3) → `render`(4) → REVIEWING
- REVIEWING이 되는 순간 결과를 만든다:
  - 제목: `"가짜 동화 {runNo}"`, 8쪽 본문 (`"{n}쪽 이야기 (실행 {runNo})"`)
  - **실제 PNG 파일을 `FileStorage`에 쓴다**: 작은 단색 이미지(쪽마다 다른 색), 캐릭터 시트 1개
  - 그림만 다시: 고른 쪽만 새 경로에 쓰고, 나머지 쪽은 **이전 실행의 key를 그대로** 돌려준다
  - 가이드: 대화 질문 2개(after_page 3, 6)
  - checkpointId: `"fake-cp-{runNo}"`, cost: 고정값
- 실패 흉내: 동화 입력의 전하고 싶은 말에 `[[FAIL]]`이 있으면 첫 실행은 FAILED. retry는 성공
- 결과 위기 신호 흉내: `[[OUTPUT_REVISE]]`가 있으면 결과 safety를 REVISE로

## 3. 생성 시작과 진행 확인 스케줄러

### 생성 시작: `POST /api/stories/{id}/confirm` (CRT-06)

1. 상태 CONFIRMING이어야 함. `toneRequired`인데 tone이 없으면 `TONE_REQUIRED`
2. runNo = 이 동화의 마지막 runNo + 1, runId = 새 UUID, outputPrefix 계산
3. **트랜잭션 밖에서** `AiClient.generate(...)` (finalContext와 tone은 AI 쪽 thread에 이미 있으므로 tone만 전달)
4. 저장: `GenerationRun.start(... INITIAL, ALL ...)`, 스토리 `CONFIRMING → REQUESTED`
5. 알림 STORY_STARTED (7장)
6. 응답 `202 GenerationStatusResponse`

### 진행 확인: `GET /api/stories/{id}/generation`

**DB만 읽는다. AI를 부르지 않는다** (프론트가 3초마다 불러도 AI에 부담이 없게).

```json
{
  "storyId": 12,
  "status": "GENERATING_IMAGES",
  "runNo": 1,
  "stage": 3,
  "stageLabel": "그림 그리기",
  "startedAt": "2026-10-21T05:30:00Z",
  "error": null
}
```

stage: `design_story` 1 이야기 짜기 / `write_story`, `polish_story` 2 글 쓰기 / `reading_guide`, `illustrate`, `illustrate_only` 3 그림 그리기 / `render` 4 책으로 묶기

### 스케줄러: `GenerationPoller`

`@Scheduled(fixedDelayString = "${firstory.generation.poll-interval}")`, `poll-enabled=false`면 등록하지 않는다. 테스트는 `pollOnce()`를 직접 부른다.

1. RUNNING 실행을 가져온다. **서버가 여러 대여도 같은 실행을 동시에 처리하지 않도록** `SELECT ... FOR UPDATE SKIP LOCKED`로 짧게 잡고 (`@Lock` + `jakarta.persistence.lock.timeout = -2` 힌트, 또는 네이티브 쿼리), 한 번에 최대 20개
2. 실행마다 **트랜잭션 밖에서** `AiClient.status`
3. 결과별 처리 (실행 하나 실패가 다른 실행을 막지 않게 각각 try/catch)

| AI state | 처리 |
|---|---|
| RUNNING | `run.progress(node)`. 스토리 상태: stage 1~2면 GENERATING_TEXT, 3~4면 GENERATING_IMAGES (가능한 전이만, 같은 상태면 그대로) |
| REVIEWING | `AiClient.result` → 4장 결과 저장 |
| FAILED | `run.fail(code)`, 스토리 FAILED, 알림 STORY_FAILED |
| 시작 후 `timeout` 지남 | `run.fail("TIMEOUT")`, 스토리 FAILED, 알림 STORY_FAILED |
| AI 연결 실패 | 아무것도 바꾸지 않고 다음 주기에 다시 (timeout이 결국 처리) |

- 스토리 상태 전이는 `StoryStatus` 규칙을 지킨다. REQUESTED에서 REVIEWING으로 바로 갈 때는 허용된 순서(GENERATING_TEXT → GENERATING_IMAGES → REVIEWING)를 차례로 밟는 도우미 메서드를 둔다

## 4. 결과 저장

`GenerationResultRecorder` (한 트랜잭션):

1. **AI가 준 모든 파일 key가 `stories/{storyId}/`로 시작하는지 확인.** 아니면 저장하지 않고 실행 FAILED(`INVALID_OUTPUT_PATH`), 경고 로그. 다른 동화의 그림을 가리키는 것을 막는다
2. 쪽 수가 `page-count`(8)와 다르면 FAILED(`INVALID_PAGE_COUNT`)
3. 결과 위기 신호: `safety.level`이 REVISE 또는 CRISIS면
   - `SafetyFlag`를 source OUTPUT으로 기록 (감사용)
   - **부모의 REVISE 횟수에는 넣지 않는다** (AI가 만든 결과라 부모 책임이 아님): `SafetyGuard`의 횟수 세기에서 OUTPUT을 제외
   - 버전을 만들지 않고 실행 FAILED(`OUTPUT_UNSAFE`), 스토리 FAILED. 부모는 다시 시도 가능
4. `StoryVersion.create(story, run, versionNo = 마지막+1, parentVersion = run.baseVersion, title, checkpointId, design, characterSheetKey)`
5. `StoryPage` 8개 (text, imagePrompt, imageKey, imageStatus DONE/FAILED)
6. `ReadingGuide` + `GuidePrompt` (AI의 `text` → `question`)
7. `run.succeed(tokens, imageCount, now)`, 스토리 → REVIEWING, `story.showVersion(version)`
8. 알림 STORY_COMPLETED

## 5. 검토 API

모두 로그인, 소유자 확인(404), BLOCKED면 `STORY_BLOCKED`.

| # | 메서드 | 경로 | 화면 | 허용 상태 |
|---|---|---|---|---|
| 1 | POST | `/api/stories/{id}/confirm` | CRT-06 | CONFIRMING |
| 2 | GET | `/api/stories/{id}/generation` | GEN-01 | 모든 상태 |
| 3 | GET | `/api/stories/{id}/preview` | REV-01 | REVIEWING, COMPLETED |
| 4 | POST | `/api/stories/{id}/regenerate` | REV-02 | REVIEWING |
| 5 | GET | `/api/stories/{id}/versions` | 버전 목록 | 버전이 있는 모든 상태 |
| 6 | PUT | `/api/stories/{id}/current-version` | 버전 전환 | REVIEWING |
| 7 | POST | `/api/stories/{id}/approve` | REV-04 | REVIEWING |
| 8 | POST | `/api/stories/{id}/retry` | ST-03 | FAILED |

(그림 API 2개는 6장)

### 3. preview

```json
{
  "storyId": 12,
  "status": "REVIEWING",
  "version": { "id": 31, "versionNo": 2, "title": "…" },
  "characterSheetUrl": "/api/stories/12/versions/31/character-sheet",
  "pages": [
    { "pageNo": 1, "text": "아로는 **쿵쿵**…", "imageStatus": "DONE",
      "imageUrl": "/api/stories/12/versions/31/pages/1/image" }
  ],
  "regenerateRemaining": 2
}
```

- 그림이 실패한 쪽은 `imageUrl: null`, `imageStatus: "FAILED"`
- URL에 파일 key나 저장 경로를 절대 넣지 않는다 (DB id로만)

### 4. regenerate

```json
{ "scope": "IMAGES", "pages": [3, 5], "feedback": "주인공 표정을 더 밝게" }
```

1. scope는 CHARACTER_STORY, TEXT, IMAGES (ALL은 거부)
2. IMAGES: pages는 1~page-count 안의 중복 없는 값, 비어 있으면 전체. 다른 scope에 pages가 오면 `INVALID_PAGE_SELECTION`
3. feedback(최대 300자)이 있으면 **먼저** `SafetyGuard.check(REGEN_FEEDBACK)` → ASK_REVISE면 다시 만들지 않고 응답(횟수 차감 없음), BLOCK이면 차단
4. `story.requestRegeneration(maxRegenerations)` (한도 초과면 `REGENERATION_LIMIT_REACHED`)
5. 새 실행: REGENERATE, scope, targetPages, baseVersion = 현재 버전, feedback
6. **트랜잭션 밖에서** `AiClient.regenerate(..., baseCheckpointId = 현재 버전의 checkpointId, ...)`. AI 실패면 4~5를 저장하지 않는다
7. 응답 `202` + `StoryStepResponse` 형식(safety 포함)

### 5. versions

```json
[
  { "id": 30, "versionNo": 1, "parentVersionNo": null, "title": "…", "scope": "ALL", "createdAt": "…", "current": false },
  { "id": 31, "versionNo": 2, "parentVersionNo": 1, "title": "…", "scope": "IMAGES", "createdAt": "…", "current": true }
]
```

### 6. current-version

`{versionId}` → `story.showVersion(version)`. 다른 동화의 버전이면 404. **다시 만들기 횟수는 바뀌지 않는다.** AI는 부르지 않는다.

### 7. approve

1. `story.confirm(now)` 저장 (Spring이 기준)
2. **트랜잭션 밖에서** `AiClient.approve` — 실패해도 확정은 유지하고 경고 로그 (AI 쪽 thread는 정리만 안 될 뿐)
3. 확정 시에는 알림을 보내지 않는다 (PRD 33)
4. 응답 `200 {status: "COMPLETED"}`

### 8. retry

마지막 실패 실행과 같은 scope, targetPages, baseVersion으로 새 실행(RETRY) → `story.retry()` → `AiClient.retry`. **다시 만들기 횟수에 넣지 않는다.**

### ErrorCode 추가

| 코드 | HTTP | 메시지 |
|---|---|---|
| TONE_REQUIRED | 400 | 이야기 분위기를 먼저 골라 주세요. |
| INVALID_PAGE_SELECTION | 400 | 다시 그릴 쪽을 다시 골라 주세요. |
| FEEDBACK_TOO_LONG | 400 | 요청 메모는 300자까지 쓸 수 있어요. |
| VERSION_NOT_FOUND | 404 | 이 동화의 버전을 찾을 수 없어요. |
| IMAGE_NOT_FOUND | 404 | 그림을 찾을 수 없어요. |

## 6. 그림 내려주기 API

| 메서드 | 경로 |
|---|---|
| GET | `/api/stories/{id}/versions/{versionId}/pages/{pageNo}/image` |
| GET | `/api/stories/{id}/versions/{versionId}/character-sheet` |

보안 규칙 (모두 지킨다):
- 로그인 필요, `findByIdAndUserId`로 동화 확인 → 버전이 그 동화의 것인지 → 페이지가 그 버전의 것인지. 하나라도 아니면 404 (403 아님)
- **파일 위치는 DB에 저장된 key로만 찾는다.** 요청 값으로 경로를 만들지 않는다
- key가 `stories/{storyId}/`로 시작하는지 한 번 더 확인
- 응답 헤더: `Content-Type: image/png`, `X-Content-Type-Options: nosniff`, `Cache-Control: private, max-age=3600`, `Content-Disposition: inline`
- 파일이 없거나 imageStatus가 FAILED면 `IMAGE_NOT_FOUND`
- 나중에 S3로 바꿔도 **이 API 주소와 동작은 그대로** 두고 `FileStorage` 구현만 바꾼다 (프론트 수정 없음)

## 7. 알림 기록

`NotificationService.notify(GenerationRun run, NotificationType type)`:

1. 회원의 최신 SMS_NOTIFY 동의가 활성이 아니면 아무것도 하지 않음
2. 같은 (run, type)이 이미 있으면 아무것도 하지 않음 (UNIQUE)
3. `Notification.pending(...)` 저장 → **트랜잭션 밖에서** `SmsSender.send` → `sent`/`failed` 저장
4. 문구는 `NotificationTemplates`에 임시값으로 // TODO(PM): 문구 확정
   - STORY_STARTED: "[FIRSTORY] 맞춤 동화를 만들기 시작했어요. 3~4분 뒤에 확인해 주세요."
   - STORY_COMPLETED: "[FIRSTORY] 맞춤 동화가 완성됐어요. 확인하고 아이와 함께 읽어 보세요."
   - STORY_FAILED: "[FIRSTORY] 동화를 만들지 못했어요. 사이트에서 다시 시도할 수 있어요."
5. 문자에 아이 정보, 동화 제목, 부모 입력을 넣지 않는다
6. STORY_STARTED는 **첫 생성(INITIAL)에만** 보낸다. 다시 만들기·다시 시도는 완료·실패만  // TODO(PM)
7. 알림 실패가 생성 흐름을 깨뜨리지 않는다 (예외를 삼키고 로그)

---

## 8. 테스트

### 단위 테스트

| 대상 | 확인할 것 |
|---|---|
| `LocalFileStorage` | `../etc/passwd`, 절대 경로, 역슬래시 key 거부 / 루트 안 정상 key 읽기·쓰기 |
| stage 변환 | 노드 이름 → stage, 모르는 노드는 현재 stage 유지 |
| `FakeAiClient` | status 호출마다 단계 진행 / 그림만 다시는 고른 쪽만 새 key, 나머지는 이전 key / `[[FAIL]]` 첫 실행 실패 |

### 통합 테스트 (MockMvc + Testcontainers + FakeAiClient, poll-enabled=false, `pollOnce()` 직접 호출)

| 시나리오 | 확인할 것 |
|---|---|
| 정상 생성 | confirm 202 → pollOnce ×5 → REVIEWING, 버전 1, 페이지 8, 가이드 질문 2, 실행 SUCCEEDED, 비용 저장 |
| 진행 상태 | pollOnce마다 stage 1→2→3→4, 스토리 상태 GENERATING_TEXT → GENERATING_IMAGES |
| 진행 조회는 AI 미호출 | `GET /generation`을 여러 번 불러도 AI status 호출 수가 늘지 않음 |
| tone 필수 | toneRequired인데 tone 없음 → TONE_REQUIRED |
| 미리보기 | 8쪽, imageUrl에 key·경로 없음 |
| 그림 API | 200 + PNG 바이트 + 보안 헤더 / 다른 회원 404 / 다른 동화의 versionId 404 / 없는 쪽 404 |
| 그림만 다시 | pages [3] → 버전 2, 3쪽만 새 key, 나머지는 버전 1과 같은 key / regenerateRemaining 감소 |
| 다시 만들기 한도 | 3번 성공 후 4번째 `REGENERATION_LIMIT_REACHED` |
| 메모 위기 신호 | `[[REVISE]]` 메모 → ASK_REVISE, 횟수 그대로, 새 실행 없음 / 두 번째 → BLOCKED |
| 버전 나무 | v1 → 글 다시 v2 → v1으로 전환 → 그림 다시 v3: v3.parent = v1, AI regenerate에 v1의 checkpointId 전달, v2 그대로 |
| 버전 전환 | 횟수 그대로, AI 미호출 |
| 확정 | COMPLETED, confirmedAt, 알림 미발송 / AI approve 실패해도 COMPLETED |
| 실패와 재시도 | `[[FAIL]]` → FAILED + STORY_FAILED 알림 → retry → 성공, 다시 만들기 횟수 그대로 |
| 시간 초과 | Clock을 16분 이동 후 pollOnce → FAILED(TIMEOUT) |
| 결과 위기 신호 | `[[OUTPUT_REVISE]]` → FAILED(OUTPUT_UNSAFE), safety_flags source OUTPUT, 부모 REVISE 횟수에 안 들어감 |
| 경로 검사 | 가짜 결과가 다른 동화 key를 주게 설정 → FAILED(INVALID_OUTPUT_PATH), 버전 없음 |
| 알림 | SMS_NOTIFY 동의 시 STARTED·COMPLETED 기록, 미동의 시 없음, 같은 실행 중복 없음, 다시 만들기는 STARTED 없음 |
| 동시 처리 | pollOnce 두 개를 동시에 실행해도 같은 실행의 버전이 두 번 생기지 않음 |

### 문서 갱신

- `CLAUDE.md`: 생성 흐름(스케줄러, 진행 조회는 DB만), 파일 저장소와 그림 API 보안 규칙, 결과 위기 신호 처리, 알림 규칙, data/ 폴더, 작업 문서 목록에 TASK-06
- Swagger: 새 API에 한국어 요약과 오류 코드. 그림 API는 `image/png` 응답으로 표시

---

## 완료 조건

- [ ] `./gradlew build` 통과 (기존 테스트 포함 전부)
- [ ] V1~V5 SQL 내용을 바꾸지 않았다
- [ ] DB 트랜잭션 안에서 AI나 문자 발송을 호출하지 않는다
- [ ] 진행 조회 API는 AI를 부르지 않는다
- [ ] 그림 API는 DB의 key로만 파일을 찾고, 소유자·버전·쪽을 모두 확인한다
- [ ] 응답 어디에도 파일 key나 서버 경로가 없다
- [ ] 버전 전환과 다시 시도는 다시 만들기 횟수에 들어가지 않는다
- [ ] 서버가 여러 대여도 같은 실행을 두 번 처리하지 않는다
- [ ] local 프로필에서 AI 서버 없이 Swagger로 확인 → 생성 → 미리보기 → 그림 보기 → 다시 만들기 → 확정까지 해볼 수 있다
- [ ] 커밋은 빌드가 통과한 상태로만 (`&&`, 파이프 없이)
