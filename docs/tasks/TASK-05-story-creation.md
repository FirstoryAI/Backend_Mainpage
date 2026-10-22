# TASK-05. 동화 만들기 (입력, 위기 신호, AI 인터뷰)

`CLAUDE.md`를 먼저 읽고 시작한다. 이 작업이 끝나면 부모가 CRT-01부터 CRT-05까지 진행해서 **생성 전 확인(CRT-06) 직전 상태**까지 갈 수 있어야 한다. 실제 생성 요청(확인 버튼 이후)은 TASK-06에서 한다.

AI 서버(FastAPI)는 아직 없다. 스펙 `docs/FIRSTORY-AI-API-SPEC-v0.3.md`대로 호출하는 클라이언트를 만들고, 로컬과 테스트에서는 가짜 클라이언트로 돌린다.

## 범위

**포함**: 저장소 정리, V4, AI 클라이언트(실제·가짜), 위기 신호 처리 서비스, 동화 만들기 API 9개, 테스트

**제외**: 생성 전 확인 버튼 이후(생성 요청, 상태 폴링, 결과, 다시 만들기)는 TASK-06. 서재는 TASK-07.

## 작업 순서

각 단계 끝에 `./gradlew build` 통과 후 커밋. **빌드 명령에 파이프(`| tail` 등)를 붙이지 않는다** (파이프가 있으면 `&&`가 빌드 실패를 못 잡는다). 마지막에 push.

0. 저장소 정리
1. V4와 설정
2. AI 클라이언트
3. 위기 신호 처리 서비스
4. 입력 API (CRT-01~04, ST-04)
5. 인터뷰 API (CRT-05)
6. 테스트, 문서 갱신

---

## 0. 저장소 정리

- 루트의 `FIRSTORY-AI-API-SPEC-v0.2.md`는 지운다. 이 zip의 `docs/FIRSTORY-AI-API-SPEC-v0.3.md`를 `docs/`에 넣어 커밋한다.
- 루트의 `firstory-task-04.zip`, 이번 zip 파일은 커밋하지 않고 지운다.
- `.gitignore`에 `*.zip`을 추가한다.

## 1. V4와 설정

`src/main/resources/db/migration/V4__story_tone_required.sql`은 **이미 제공되어 있다. 내용을 바꾸지 않는다.** `story_inputs.tone_required`(boolean) 추가.

`StoryInput`에 `toneRequired` 필드와 `markToneRequired()` 추가. `chooseTone(tone)`은 기존 그대로.

설정:

```yaml
firstory:
  ai:
    mode: ${AI_MODE:http}            # http | fake. local 프로필은 fake
    base-url: ${AI_BASE_URL:http://localhost:8000}
    internal-token: ${AI_INTERNAL_TOKEN:}
    connect-timeout: 3s
    read-timeout: 35s
```

- `mode=http`인데 `internal-token`이 비어 있으면 기동 실패
- `application-local.yml`(과 example)은 `mode: fake`

## 2. AI 클라이언트

### 인터페이스 (`global.ai`)

```java
public interface AiClient {
    SafetyResult checkSafety(SafetySource source, List<String> texts, String questionContext);
    InterviewReply startInterview(String threadId, UUID runId, Map<String, Object> context, Tone tone);
    InterviewReply answerInterview(String threadId, String answer);   // answer가 null이면 skip
}
```

- `SafetyResult(SafetyLevel level, String category, String reason)`
- `InterviewReply`: `type`(QUESTION | CONFIRM), `turn`, `question`, `contextUpdate`, `summary`, `endReason`(InterviewEndReason), `finalContext`
- 생성 관련 메서드(generate, status, result, review)는 TASK-06에서 추가

### HttpAiClient (`mode=http`)

- Spring `RestClient`, 스펙 v0.3의 경로·JSON 그대로 (`snake_case`)
- 모든 요청에 `X-Internal-Token`
- `source` 값: FORM → `form`, INTERVIEW → `interview_answer`, REGEN_FEEDBACK → `regen_feedback`
- 오류 변환:

| AI 응답 | Spring |
|---|---|
| 연결 실패, 타임아웃, 5xx | `AI_UNAVAILABLE` |
| 409 `THREAD_BUSY` | `AI_BUSY` |
| 409 `INVALID_STATE`, 404 `THREAD_NOT_FOUND` | `AI_STATE_MISMATCH` (로그에 thread_id 남김, 본문 내용은 남기지 않음) |
| 422 | `AI_UNAVAILABLE` + 로그에 경고 (우리 쪽 형식 버그) |

- **요청·응답 본문을 로그에 남기지 않는다** (부모 입력, 아이 정보가 들어 있음)

### FakeAiClient (`mode=fake`)

local과 테스트에서 진짜 AI 없이 전체 흐름을 돌리기 위한 것. 동작을 예측할 수 있게 고정한다.

- `checkSafety`: 글에 `[[SENSITIVE]]`, `[[REVISE]]`, `[[CRISIS]]`가 있으면 그 level, 없으면 OK. reason은 고정 문장
- `startInterview`: 질문 1 반환 (`"아이에게 어떤 일이 있었는지 조금 더 알려 주세요"`)
- `answerInterview`: 답변 2번까지는 다음 질문, 3번째 답변이나 skip이면 CONFIRM (summary 고정 문장, endReason SUFFICIENT 또는 USER_SKIP, finalContext는 받은 context에 parent.message_direction만 채운 것)
- thread별 상태를 메모리 Map에 둔다

### ErrorCode 추가

| 코드 | HTTP | 메시지 |
|---|---|---|
| AI_UNAVAILABLE | 503 | 동화 도우미가 잠시 응답하지 않아요. 잠시 후 다시 시도해 주세요. |
| AI_BUSY | 409 | 이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요. |
| AI_STATE_MISMATCH | 409 | 진행 상태가 맞지 않아요. 화면을 새로고침해 주세요. |
| STORY_BLOCKED | 409 | 이 내용으로는 동화를 만들 수 없어요. |
| STORY_INPUT_INCOMPLETE | 400 | 상황과 아이 정보를 먼저 입력해 주세요. |

## 3. 위기 신호 처리 서비스

`domain.safety.service.SafetyGuard`:

```java
SafetyOutcome check(Story story, SafetySource source, List<String> texts, InterviewTurn turnOrNull, String questionContext);
```

1. 비어 있는 글은 빼고, 남은 글이 없으면 AI를 부르지 않고 PROCEED
2. `AiClient.checkSafety` 호출
3. level별 처리:

| level | 기록 | 결과 action |
|---|---|---|
| OK | 없음 | PROCEED |
| SENSITIVE | `storyInput.markToneRequired()` | tone이 이미 있으면 PROCEED, 없으면 CHOOSE_TONE |
| REVISE | `SafetyFlag` 저장 (occurrenceNo = 이 동화의 REVISE 수 + 1) | `SafetyPolicy.decide` → ASK_REVISE 또는 BLOCK |
| CRISIS | `SafetyFlag` 저장 | BLOCK |

4. BLOCK이면 `story.block()`
5. 결과 `SafetyOutcome(action, level, category, reason, helpKind)`
   - `helpKind`: CRISIS → `CHILD_SAFETY`, REVISE로 차단 → `PARENTING`, 그 외 null
   - 프론트가 helpKind에 맞는 상담 기관을 보여준다. **상담 기관 이름과 번호는 백엔드에 넣지 않는다** (PM 확정 전, G-FACT)

차단된 동화의 입력 데이터 처리는 `// TODO(PM): 차단된 입력을 보관할지 바로 지울지 (ST-05 확인 필요)`. 지금은 그대로 둔다.

### 트랜잭션 주의

**DB 트랜잭션 안에서 AI HTTP 호출을 하지 않는다.** AI 응답이 35초까지 걸릴 수 있어서 DB 커넥션을 붙잡게 된다.
- 순서: ① 트랜잭션으로 조회·검증 → ② 트랜잭션 밖에서 AI 호출 → ③ 새 트랜잭션으로 저장
- ①과 ③ 사이에 상태가 바뀌었으면 ③에서 다시 확인하고 `INVALID_STATUS_TRANSITION`

## 4. 입력 API

모두 로그인 필요, 동화는 `findByIdAndUserId`(없으면 `STORY_NOT_FOUND` 404). `BLOCKED` 동화에 대한 모든 변경 요청은 `STORY_BLOCKED`.

### 공통 응답: `StoryStepResponse`

```json
{
  "storyId": 12,
  "status": "DRAFT",
  "saved": true,
  "safety": { "action": "PROCEED", "level": "OK", "category": null, "reason": null, "helpKind": null },
  "toneRequired": false
}
```

- ASK_REVISE면 **입력을 저장하지 않고** `saved: false`. 프론트는 reason을 보여주고 다시 쓰게 한다
- BLOCK이면 `saved: false`, `status: BLOCKED`
- CHOOSE_TONE이면 저장하고 `saved: true`. 프론트는 ST-04를 띄운다

| # | 메서드 | 경로 | 화면 | 허용 상태 | 위기 신호 검사 |
|---|---|---|---|---|---|
| 1 | POST | `/api/stories` | 새 동화 만들기 | - | - |
| 2 | PUT | `/api/stories/{id}/situation` | CRT-01, 02 | DRAFT | description, customCategory, customReaction |
| 3 | PUT | `/api/stories/{id}/child` | CRT-03 | DRAFT | customTrait, customInterests |
| 4 | PUT | `/api/stories/{id}/message` | CRT-04 | DRAFT | message |
| 5 | PUT | `/api/stories/{id}/tone` | ST-04 | DRAFT, INTERVIEWING, CONFIRMING | - |
| 6 | GET | `/api/stories/{id}/draft` | 이어서 만들기, CRT-06 | 모든 상태 | - |
| 7 | DELETE | `/api/stories/{id}` | 나가기 | DRAFT, INTERVIEWING, CONFIRMING, BLOCKED | - |

1. **POST /api/stories**: `threadId` = 새 UUID, `Story.start`, `StoryInput.begin`. `201 {storyId, status: "DRAFT"}`
2. **situation**: 요청 `{timing, category, customCategory, description, reactions, customReaction, emotions}` → `StoryInput.writeSituation` 검증을 **먼저** 하고(형식 오류는 AI 호출 전에 400), 통과하면 위기 신호 검사 → 저장
3. **child**: 요청은 TASK-03의 `ChildSelection` 형식(`childId`, `profile`, `saveForNextTime`). 직접 입력 값이 있으면 위기 신호 검사 → 통과 시 `ChildProfileResolver.resolve` → `story.child`, `writeChild(snapshot)`
4. **message**: 요청 `{message}` (null이면 "아직 잘 모르겠어요") → 검사 → `writeParent(null, null, message, null)`
5. **tone**: 요청 `{tone}` → `chooseTone`. 응답 `StoryStepResponse`
6. **draft**: 지금까지의 입력 전체, 인터뷰 질문·답변 목록, summary, toneRequired, tone, status. 호칭은 포함해도 된다(부모 화면용). 휴대폰 등 회원 정보는 없음
7. **DELETE**: 소프트 삭제 `204`. 생성 중 이후 상태는 `INVALID_STATUS_TRANSITION` (서재 삭제는 TASK-07)

## 5. 인터뷰 API

| # | 메서드 | 경로 | 허용 상태 |
|---|---|---|---|
| 8 | POST | `/api/stories/{id}/interview/start` | DRAFT |
| 9 | POST | `/api/stories/{id}/interview/answer` | INTERVIEWING |

### 8. start

1. 상황과 아이 정보가 없으면 `STORY_INPUT_INCOMPLETE`
2. `AiContextMapper.toContext(input)`으로 context 생성 (호칭 없음), runId = 새 UUID
3. `AiClient.startInterview(threadId, runId, context, tone)`
4. 저장: `InterviewTurn.ask(story, 1, question)`, `DRAFT → INTERVIEWING`
5. 응답 `InterviewResponse`:

```json
{
  "storyId": 12,
  "status": "INTERVIEWING",
  "type": "QUESTION",
  "turn": 1,
  "question": "아이에게 어떤 일이 있었는지 조금 더 알려 주세요",
  "summary": null,
  "contextUpdate": null,
  "safety": null,
  "toneRequired": false
}
```

### 9. answer

요청 `{answer}` 또는 `{skip: true}` (둘 다 있거나 둘 다 없으면 `VALIDATION_FAILED`, answer는 500자 이하)

1. 답변이면 **AI 그래프에 보내기 전에** `SafetyGuard.check(story, INTERVIEW, [answer], 현재 turn, 현재 질문)`
   - ASK_REVISE: 답변을 저장하지 않고 AI에 보내지 않는다. 같은 질문을 그대로 돌려주고 `safety`에 reason
   - BLOCK: 차단, `status: BLOCKED`
   - CHOOSE_TONE: 진행은 계속하고 응답에 `toneRequired: true`, `safety.action: CHOOSE_TONE`
2. `AiClient.answerInterview`
3. 저장
   - 현재 turn에 `answer(text, now)`, 응답의 contextUpdate를 현재 turn에 `updateContext`
   - QUESTION이면 다음 turn `InterviewTurn.ask(story, n+1, question)`
   - CONFIRM이면 `finishInterview(summary, endReason, finalContext)`, `INTERVIEWING → CONFIRMING`, finalContext의 parent 값으로 `writeParent(response, concern, message, direction)` (값을 enum으로 바꿀 수 없으면 null로 두고 경고 로그)
4. 응답 `InterviewResponse` (CONFIRM이면 `type: "CONFIRM"`, summary 포함)

- skip은 글이 없어서 검사하지 않는다
- AI 호출이 실패하면(`AI_UNAVAILABLE`) 아무것도 저장하지 않는다. 같은 답변으로 다시 보낼 수 있다
- `// TODO(AI 연동)`: AI가 답변을 처리한 뒤 응답만 잃어버린 경우(타임아웃) 재전송하면 `AI_STATE_MISMATCH`가 날 수 있다. 진짜 AI 서버가 생기면 status API로 맞추는 처리를 TASK-06에서 추가

---

## 6. 테스트

### 단위 테스트

| 대상 | 확인할 것 |
|---|---|
| `SafetyGuard` | level 4개 × (tone 있음/없음, REVISE 1·2번째) 모든 결과 / CRISIS는 REVISE 수와 무관하게 BLOCK / 빈 글이면 AI 미호출 / helpKind |
| `FakeAiClient` | 마커별 level / 2번 답변 후 CONFIRM / skip이면 USER_SKIP |

### HttpAiClient 테스트 (`MockRestServiceServer`)

- 경로, 메서드, `X-Internal-Token` 헤더
- 요청 JSON이 스펙 v0.3 예시와 같은 키 구조 (`snake_case`)
- **요청 본문에 호칭 문자열이 없다** (호칭이 "콩이"인 아이로 만든 context를 보내고 본문에 "콩이"가 없음을 확인)
- 오류 변환 표 전부
- 타임아웃 → `AI_UNAVAILABLE`

### 통합 테스트 (MockMvc + Testcontainers + FakeAiClient)

| 시나리오 | 확인할 것 |
|---|---|
| 정상 흐름 | 생성 → situation → child → message → start → answer ×3 → `CONFIRMING`, summary, interview_turns 3개, final_context 저장 |
| skip | start → answer 1 → skip → CONFIRMING, endReason USER_SKIP |
| 폼 REVISE 1회 | situation에 `[[REVISE]]` → `saved: false`, ASK_REVISE, 입력 미저장, safety_flags 1건 |
| REVISE 2회 | 두 번째 REVISE(다른 단계라도 같은 동화) → BLOCKED, helpKind PARENTING, 이후 모든 변경 API `STORY_BLOCKED` |
| CRISIS | 인터뷰 답변에 `[[CRISIS]]` → 즉시 BLOCKED, helpKind CHILD_SAFETY, interview_turn_id 기록, 답변 미저장 |
| 인터뷰 REVISE | 답변 미저장, 같은 질문 반환, AI answerInterview 미호출 |
| SENSITIVE | `toneRequired: true`, CHOOSE_TONE / tone 선택 후 같은 SENSITIVE는 PROCEED |
| 새 동화 | 차단된 동화가 있어도 새 동화는 REVISE 횟수 0부터 |
| 상태 검사 | INTERVIEWING에서 situation 수정 → 409 / 상황 없이 start → STORY_INPUT_INCOMPLETE |
| 소유자 | 다른 회원의 동화 모든 API 404 |
| AI 장애 | FakeAiClient가 예외를 던지게 설정 → 503, 답변·턴 미저장 |
| draft 조회 | 입력, 턴, summary가 모두 보임 |
| 삭제 | DRAFT 삭제 204, 목록·조회에서 사라짐 |

### 문서 갱신

- `CLAUDE.md`: AI 클라이언트 모드(http/fake), 트랜잭션 밖 AI 호출 규칙, 위기 신호 처리 흐름, 빌드 명령에 파이프 금지, 작업 문서 목록에 TASK-05
- Swagger: 새 API 9개에 한국어 요약과 오류 코드

---

## 완료 조건

- [ ] `./gradlew build` 통과 (기존 테스트 포함 전부)
- [ ] V1~V4 SQL 내용을 바꾸지 않았다
- [ ] DB 트랜잭션 안에서 AI를 호출하는 코드가 없다
- [ ] AI로 가는 요청 본문과 로그 어디에도 호칭, 부모 입력 원문이 로그로 남지 않는다
- [ ] 위기 신호로 다시 쓰기를 요청한 입력은 저장되지도, AI 그래프로 가지도 않는다
- [ ] 상담 기관 정보가 백엔드 코드에 없다 (helpKind만)
- [ ] local 프로필에서 AI 서버 없이 Swagger로 CRT-01~05 전체 흐름을 해볼 수 있다
- [ ] 커밋은 빌드가 통과한 상태로만 (`&&`, 파이프 없이)
