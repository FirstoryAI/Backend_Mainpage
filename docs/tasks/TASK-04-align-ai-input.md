# TASK-04. AI 입력 형식 맞추기

`CLAUDE.md`를 먼저 읽고 시작한다. AI 파이프라인 코드를 직접 확인한 결과, 우리 입력 구조가 AI가 받는 형식(`StoryInputContext`)과 달랐다. 이 작업에서 DB, 엔티티, 아이 프로필 API를 AI 형식에 맞추고, 동화 만들기(TASK-05)가 그대로 쓸 **AI 입력 변환기**를 만든다.

## 범위

**포함**: V3 마이그레이션 적용, enum과 엔티티 변경, 아이 프로필 API 형식 변경, 입력 검증 규칙, 위기 신호 3단계 정책, `AiContextMapper`, 설정값 추가, 테스트

**제외**: 동화 만들기 API, AI 서버 호출 (TASK-05)

## 배경: AI가 받는 입력 형식

AI 저장소 `schemas.py`의 `StoryInputContext` 기준이다. **값 이름은 AI와 같은 단어를 쓰고, DB와 Java는 대문자, AI로 보낼 때 소문자로 바꾼다.** 이름을 서로 다르게 짓고 변환표를 두지 않는다.

```json
{
  "situation": {
    "category": "friend_conflict",
    "custom_category": null,
    "timing": "past",
    "description": "최대 60자"
  },
  "child": {
    "age_months": 63,
    "gender": "female",
    "traits": ["shy", "cautious"],
    "custom_trait": null,
    "interests": ["dinosaur", "other"],
    "custom_interest": "파란색, 기차"
  },
  "moment": {
    "reactions": ["cried", "other"],
    "custom_reaction": "방에 숨었어요",
    "emotions": ["sad", "scared"]
  },
  "parent": {
    "response": null,
    "concern": null,
    "message": "친구에게 먼저 말을 걸어보는 용기를 주고 싶어요",
    "message_direction": "perspective_taking"
  }
}
```

## 결정 사항

| 항목 | 값 |
|---|---|
| 동화 쪽수 | 8쪽. DB에 박지 않고 `firstory.story.page-count: 8` |
| 상황 분류 | FRIEND_CONFLICT, NEW_ENVIRONMENT, SEPARATION, SAFETY, RULES, FAMILY_CHANGE, FEAR, OTHER. OTHER면 직접 입력 필수 (30자) |
| 상황 설명 | 최대 60자 |
| 아이 반응 | 여러 개: CRIED, SILENT, ANGRY_OUTBURST, CLINGY, AVOIDED, ASKED_QUESTIONS, OTHER. OTHER면 직접 입력 필수 (30자) |
| 감정 | 여러 개: SAD, ANGRY, SCARED, CONFUSED, ASHAMED, EXCITED, UNKNOWN |
| 반응·감정과 시점 | UPCOMING이면 반응과 감정은 비어 있어야 한다 (아직 일어나지 않은 일) |
| 성향 | 여러 개: SHY, ACTIVE, SENSITIVE, CURIOUS, STUBBORN, CAUTIOUS, OTHER. OTHER면 직접 입력 필수 (20자) |
| 성별 | FEMALE, MALE, UNSPECIFIED |
| 좋아하는 것 | 목록(DINOSAUR, SPACE, ANIMAL, VEHICLE, PRINCESS, HERO, DRAWING) + 직접 입력, **합쳐서 최대 5개**, 직접 입력 한 개당 20자 |
| 전하고 싶은 방향 | COMFORT_FIRST, PERSPECTIVE_TAKING, EXPLAIN_SITUATION, SET_BOUNDARY, REASSURE, UNDECIDED |
| 위기 신호 | 단계: OK, SENSITIVE, REVISE, CRISIS. REVISE는 `firstory.policy.safety-revise-limit`(2)번째에 차단, CRISIS는 1번만에 차단 |
| 다시 만들기 | 최대 3회 (`firstory.policy.max-regenerations`, 이미 있음) |

---

## 작업 순서

각 단계 끝에 `./gradlew build` 통과 후 커밋 (`&&`로 이어서). 마지막에 push.

1. V3 적용과 enum 정리
2. 아이 프로필 변경
3. 만들기 입력 변경
4. 위기 신호 3단계
5. AI 입력 변환기
6. 테스트, 문서 갱신

---

## 1. V3 적용과 enum 정리

`src/main/resources/db/migration/V3__align_ai_input.sql`은 **이미 제공되어 있다. 내용을 바꾸지 않는다.** (PostgreSQL 16에서 V1, V2 뒤에 적용과 옛 데이터 변환까지 검증함)

V3가 하는 일:
- `children.interests` → `custom_interests`로 이름 변경, `interest_codes`, `custom_trait` 추가
- `UNDISCLOSED` → `UNSPECIFIED`, `SLOW_TO_WARM` → `CAUTIOUS` 데이터 변환
- `story_inputs`: `moment_reaction`, `emotion` 삭제. `situation_custom_category`, `moment_reactions`, `moment_custom_reaction`, `emotions`, `parent_response`, `parent_concern`, `final_context` 추가
- `story_pages.page_no`, `guide_prompts.after_page` 상한 제거 (1 이상만)
- `safety_flags.level` 추가, UNIQUE를 `(story_id, level, occurrence_no)`로 변경

### enum

모든 enum에 `label`(화면 문구)과 `aiValue()`(소문자 이름)를 둔다. `aiValue()`는 `name().toLowerCase()`.

| enum | 위치 | 값과 label |
|---|---|---|
| `Gender` | child.entity | FEMALE(여자아이), MALE(남자아이), UNSPECIFIED(밝히지 않을래요) |
| `Trait` | child.entity | SENSITIVE(마음이 여려요), CAUTIOUS(낯선 곳에 시간이 걸려요), SHY(수줍음이 많아요), ACTIVE(활발해요), CURIOUS(호기심이 많아요), STUBBORN(고집이 있어요), OTHER(기타) |
| `InterestCode` (신규) | child.entity | DINOSAUR(공룡), SPACE(우주), ANIMAL(동물), VEHICLE(탈것), PRINCESS(공주), HERO(히어로), DRAWING(그림 그리기)  // TODO(PM): 화면 문구 확정 |
| `SituationCategory` (신규) | story.entity | FRIEND_CONFLICT(친구와 다툼), NEW_ENVIRONMENT(새로운 환경), SEPARATION(이별), SAFETY(안전), RULES(규칙), FAMILY_CHANGE(가족의 변화), FEAR(두려움), OTHER(기타)  // TODO(PM): 화면 문구 확정 |
| `MomentReaction` (신규) | story.entity | CRIED(울었어요), SILENT(말이 없었어요), ANGRY_OUTBURST(화를 냈어요), CLINGY(떨어지지 않으려 했어요), AVOIDED(피했어요), ASKED_QUESTIONS(질문을 했어요), OTHER(기타)  // TODO(PM) |
| `Emotion` (신규) | story.entity | SAD(슬퍼요), ANGRY(화나요), SCARED(무서워요), CONFUSED(헷갈려요), ASHAMED(부끄러워요), EXCITED(설레요), UNKNOWN(잘 모르겠어요)  // TODO(PM) |
| `MessageDirection` (신규) | story.entity | COMFORT_FIRST, PERSPECTIVE_TAKING, EXPLAIN_SITUATION, SET_BOUNDARY, REASSURE, UNDECIDED (label은 인터뷰 결과 표시용, TODO(PM)) |
| `SituationTiming` | story.entity | PAST, UPCOMING (변경 없음) |

- 화면 문구(label)는 임시값이다. 코드 상수가 아니라 enum 한곳에만 두고, 선택지 API로 내려준다.
- enum 값 순서는 위 표 순서 그대로 (응답 정렬 기준).

### 설정값 추가 (`FirstoryProperties`)

```yaml
firstory:
  story:
    page-count: 8
  policy:
    max-regenerations: 3
    safety-revise-limit: 2
```

---

## 2. 아이 프로필 변경

### ErrorCode 추가·변경

| 코드 | HTTP | 메시지 |
|---|---|---|
| CUSTOM_VALUE_REQUIRED | 400 | 기타를 고르면 내용을 직접 입력해 주세요. |
| CUSTOM_VALUE_TOO_LONG | 400 | 직접 입력은 {n}자까지 쓸 수 있어요. (n은 항목별 값으로 채움) |
| INTERESTS_LIMIT_EXCEEDED | (기존) | 좋아하는 것은 고른 것과 직접 입력을 합쳐 5개까지예요. |

`INTEREST_TOO_LONG`은 `CUSTOM_VALUE_TOO_LONG`으로 통합한다.

### `Child` 엔티티

- `interests`(List<String>) → `interestCodes`(List<InterestCode>, jsonb `interest_codes`) + `customInterests`(List<String>, jsonb `custom_interests`)
- `customTrait`(String, 20자) 추가
- `traits`에 OTHER가 있으면 `customTrait` 필수, 없으면 `customTrait`는 null로 저장 (보내와도 버림)

### `Interests` 정책 변경

`Interests.normalize(List<InterestCode> codes, List<String> customs)` → 정리된 두 목록을 담은 record:
- codes: 중복 제거, enum 선언 순서로 정렬
- customs: strip, 빈 값 제거, 순서 유지 중복 제거, 20자 초과면 `CUSTOM_VALUE_TOO_LONG`
- codes 개수 + customs 개수가 5 초과면 `INTERESTS_LIMIT_EXCEEDED`

### 아이 프로필 API 요청·응답

```json
{
  "birthYm": "2021-03",
  "gender": "FEMALE",
  "traits": ["SHY", "CAUTIOUS", "OTHER"],
  "customTrait": "겁이 많아요",
  "interestCodes": ["DINOSAUR"],
  "customInterests": ["파란색", "기차"]
}
```

응답(`ChildResponse`)도 같은 필드 + `id`, `displayName`, `ageMonths`.

`GET /api/children/options` 응답에 추가: `interests: [{code, label}]`, `customTraitMaxLength: 20`, `customInterestMaxLength: 20`, `interestsMax: 5`.

### `ChildProfileResolver`, `ChildSnapshot`

- `ChildProfile` record에 `customTrait`, `interestCodes`, `customInterests` 반영
- `ChildSnapshot(ageMonths, gender, traits, customTrait, interestCodes, customInterests)`
  - **호칭(displayName)은 여전히 넣지 않는다**
  - 저장된 jsonb의 필드 이름이 바뀌므로, 기존 `story_inputs.child_snapshot` 데이터는 로컬 개발 데이터뿐이라 호환 처리하지 않는다 (CLAUDE.md에 기록)
- 다섯 경우 규칙은 TASK-03 그대로

---

## 3. 만들기 입력 변경

### `StoryInput` 엔티티

| 필드 | 타입 | 비고 |
|---|---|---|
| situationTiming | SituationTiming | |
| situationCategory | SituationCategory | (기존 String → enum, 컬럼 그대로 varchar) |
| situationCustomCategory | String(30) | OTHER일 때만 |
| description | String | **60자 이하** |
| momentReactions | List<MomentReaction> jsonb | |
| momentCustomReaction | String(30) | OTHER일 때만 |
| emotions | List<Emotion> jsonb | |
| parentResponse, parentConcern | String text | 주로 인터뷰 결과로 채움 |
| parentMessage | String text | |
| messageDirection | MessageDirection | (기존 String → enum) |
| tone | Tone | 변경 없음 |
| childSnapshot | ChildSnapshot jsonb | |
| interviewSummary, interviewEndReason | | 변경 없음 |
| finalContext | Map<String,Object> jsonb | 인터뷰가 끝난 뒤 AI가 확정한 context 전체. AI가 채운 parent 값의 원본 |

메서드:
- `writeSituation(timing, category, customCategory, description, reactions, customReaction, emotions)`
  - description 60자 초과 → `DESCRIPTION_TOO_LONG`
  - OTHER인데 직접 입력 없음 → `CUSTOM_VALUE_REQUIRED`, OTHER가 아닌데 직접 입력 → 버림
  - UPCOMING인데 reactions 또는 emotions가 있으면 → `MOMENT_ONLY_FOR_PAST` (기존 `EMOTION_ONLY_FOR_PAST` 이름 변경)
  - 목록은 중복 제거, enum 순서 정렬
- `writeParent(response, concern, message, direction)`
- `finishInterview(summary, reason, finalContext)` (finalContext 인자 추가)

ErrorCode:

| 코드 | HTTP | 메시지 |
|---|---|---|
| DESCRIPTION_TOO_LONG | 400 | 상황 설명은 60자까지 쓸 수 있어요. |
| MOMENT_ONLY_FOR_PAST | 400 | 아이의 반응과 감정은 있었던 일에서만 고를 수 있어요. |

---

## 4. 위기 신호 3단계

### enum

- `SafetyLevel`: OK, SENSITIVE, REVISE, CRISIS (AI 응답 그대로. 기록은 REVISE, CRISIS만)
- `SafetySource`: FORM, INTERVIEW, REGEN_FEEDBACK, OUTPUT
- `SafetyUserResponse`: REVISED(다시 씀), DISMISSED
- `SafetyAction`: PROCEED(그대로 진행), CHOOSE_TONE(ST-04 분위기 선택으로), ASK_REVISE(다시 쓰기 안내), BLOCK(ST-05)

### `SafetyPolicy` (상수 → 설정값 사용하는 컴포넌트로)

`decide(SafetyLevel level, int reviseCountIncludingThis, int reviseLimit) → SafetyAction`

| level | 결과 |
|---|---|
| OK | PROCEED |
| SENSITIVE | CHOOSE_TONE (이미 tone을 골랐으면 PROCEED. 판단은 호출하는 쪽에서) |
| REVISE | 횟수 < limit → ASK_REVISE, 횟수 ≥ limit → BLOCK |
| CRISIS | BLOCK |

- 횟수는 **동화 단위, REVISE 단계만** 센다: `SafetyFlagRepository.countByStoryIdAndLevel`
- 기본 limit 2: 1번째 REVISE는 다시 쓰기 안내, 2번째는 차단

### `SafetyFlag`

- `level` 필드 추가 (REVISE, CRISIS만 저장. OK·SENSITIVE를 넣으려 하면 `IllegalArgumentException`)
- `occurrenceNo`는 같은 동화, 같은 level 안에서의 순번

---

## 5. AI 입력 변환기

`domain.story.service.AiContextMapper` (순수 변환, Spring 의존 없음):

`Map<String, Object> toContext(StoryInput input)` → 위 "배경"의 JSON과 같은 구조

규칙:
- 모든 enum 값은 `aiValue()`(소문자)
- `child.interests`: interestCodes의 소문자 값 + customInterests가 있으면 `"other"` 추가
- `child.custom_interest`: customInterests를 `", "`로 이어 붙인 문자열, 없으면 null  (AI가 한 칸만 받기 때문)
- `child.custom_trait`, `situation.custom_category`, `moment.custom_reaction`: 해당 OTHER가 있을 때만 값, 아니면 null
- UPCOMING이면 `moment`는 빈 목록들
- `parent`는 값이 없는 필드도 키는 넣고 null
- **호칭은 어떤 경우에도 넣지 않는다**
- 키 이름은 AI와 같은 snake_case

---

## 6. 테스트

### 단위 테스트

| 대상 | 확인할 것 |
|---|---|
| enum `aiValue()` | 모든 enum의 모든 값이 소문자 이름과 같음 (반복문으로 전수 검사) |
| `Interests.normalize` | 목록 3 + 직접 2 = 통과 / 목록 3 + 직접 3 = 예외 / 직접 21자 예외 / 중복·공백 정리 |
| 성향 OTHER | OTHER + customTrait 없음 → 예외 / OTHER 없이 customTrait → 버려짐 / 21자 → 예외 |
| `StoryInput.writeSituation` | 61자 예외 / OTHER 직접 입력 필수 / UPCOMING + 감정 → 예외 / 중복 제거·정렬 |
| `SafetyPolicy` | 표의 모든 경우 / limit을 3으로 바꾸면 2번째는 ASK_REVISE, 3번째는 BLOCK |
| `AiContextMapper` | 위 "배경" JSON과 **필드 단위로 똑같은** 결과 (기대 JSON을 테스트 리소스 파일로 두고 비교) / 직접 입력 없을 때 "other" 없음 / UPCOMING이면 moment 비어 있음 / 결과 어디에도 displayName 값이 없음 |

### 통합 테스트

| 시나리오 | 확인할 것 |
|---|---|
| Flyway | V1→V2→V3 순서로 적용되고 validate 통과 |
| 아이 프로필 | 새 형식으로 추가·조회 왕복 / 목록 3 + 직접 3 → `INTERESTS_LIMIT_EXCEEDED` / OTHER 성향 직접 입력 왕복 |
| 선택지 API | interests 7개, traits 7개(OTHER 포함), genders 3개와 label |
| jsonb 왕복 | StoryInput의 momentReactions, emotions, finalContext, childSnapshot이 그대로 돌아옴 |
| 위기 신호 횟수 | 같은 동화에 REVISE 1개 → 다음 REVISE는 BLOCK / CRISIS는 REVISE 횟수와 별개 |
| 기존 테스트 | TASK-01~03 테스트가 새 형식에 맞게 고쳐져서 모두 통과 |

### 문서 갱신

- `CLAUDE.md`: 값 이름 규칙(AI와 같은 단어, DB 대문자, AI 전송 시 소문자), 쪽수 설정값, 위기 신호 3단계와 설정값, 화면 문구 TODO(PM) 목록, child_snapshot 형식 변경 기록
- `docs/tasks/` 목록에 TASK-04 추가

---

## 완료 조건

- [ ] `./gradlew build` 통과 (기존 테스트 포함 전부)
- [ ] V1, V2, V3 SQL 내용을 바꾸지 않았다
- [ ] 모든 enum 값이 AI 값과 소문자 기준으로 일치 (테스트로 확인)
- [ ] `AiContextMapper` 결과가 AI 입력 형식 예시와 필드 단위로 같다
- [ ] 쪽수, 다시 만들기 횟수, 위기 신호 한도가 모두 설정값이다
- [ ] 호칭이 AI 입력 어디에도 없다
- [ ] 화면 문구 임시값은 `TODO(PM)`으로 남겼다
- [ ] 커밋은 빌드가 통과한 상태로만 (`&&`)
