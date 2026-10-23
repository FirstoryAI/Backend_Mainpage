# TASK-08. 프론트 전달 준비

`CLAUDE.md`를 먼저 읽고 시작한다. 이 작업이 끝나면 **프론트 담당자가 자기 컴퓨터에서 백엔드를 띄우고, 문서만 보고 모든 화면에 API를 붙일 수 있어야 한다.** 배포는 하지 않는다.

DB 마이그레이션은 없다.

## 작업 순서

각 단계 끝에 `./gradlew build` 통과 후 커밋 (`&&`, 파이프 없이). 마지막에 push와 태그.

0. 정리
1. 가짜 AI를 진짜처럼
2. 로컬 개발 편의 (고정 인증번호, CORS)
3. 전체 흐름 통합 테스트
4. 프론트 문서
5. **실제로 띄워서 전체 흐름 확인** (이번엔 반드시)
6. 마무리

---

## 0. 정리

- 루트에 남은 `firstory-task-06/`, `firstory-task-07/` 폴더와 zip을 지운다 (커밋하지 않음)
- 루트에 이 문서가 있으면 `docs/tasks/`로 옮긴다

## 1. 가짜 AI를 진짜처럼

프론트가 가짜 AI로 개발해도 진짜 AI의 속도, 질문 수, 실패를 미리 겪어 보게 한다.

### 설정 (`firstory.ai.fake.*`)

```yaml
firstory:
  ai:
    fake:
      interview-delay: 0s          # local 예시값: 2s
      safety-delay: 0s             # local 예시값: 1s
      stage-duration: 0s           # local 예시값: 40s. 0이면 지금처럼 status 호출마다 한 단계
      turns: 2                     # 0이면 thread_id로 1~5개를 정함. local 예시값: 0
```

- test 프로필은 모두 0과 2 (지금 테스트가 그대로 돌아야 함)
- `application-local.yml.example`에 local 예시값을 넣고, 이 값의 뜻을 주석으로
- 지연은 local 전용이라 `Thread.sleep`을 써도 된다

### 단계 진행

- `stage-duration`이 0보다 크면 **생성 시작 후 지난 시간**으로 단계를 정한다 (`Clock` 기준): 경과 / stage-duration = 단계. 4단계를 넘으면 REVIEWING
- 40초면 전체 약 2분 40초로 진짜(3~4분)와 비슷하다

### 질문 수

- `turns`가 0이면 `threadId`의 해시로 1~5개를 정한다 (같은 동화는 항상 같은 수)
- 질문 문장도 5개를 준비해 순서대로 쓴다 (아래 예시)

### 그럴듯한 내용

`src/main/resources/fake-ai/sample-story.json`에 아래 샘플을 넣고 가짜 결과에 쓴다. `**의성어**` 표시, 쪽마다 다른 길이를 그대로 유지한다. 다시 만들기를 하면 제목 뒤에 `(다시 {n})`을 붙여 버전이 구분되게 한다.

```json
{
  "title": "공룡 아로의 첫 인사",
  "pages": [
    "아로는 작은 초록 공룡이에요. 오늘은 새 어린이집에 가는 날이에요.",
    "문 앞에서 아로의 발이 멈췄어요. 가슴이 **콩닥콩닥** 뛰었어요.",
    "교실 안에서는 친구들이 블록을 쌓고 있었어요. **와르르!** 탑이 무너지자 다 같이 웃었어요.",
    "아로도 웃고 싶었지만, 말이 목에 걸려서 나오지 않았어요. 아로는 구석에 혼자 앉았어요.",
    "그때 노란 날개를 단 친구 피피가 다가왔어요. \"같이 쌓을래?\"",
    "아로는 고개만 **끄덕끄덕** 했어요. 둘이서 블록을 하나씩 올렸어요.",
    "탑이 아로 키만큼 높아졌어요. 아로가 작게 말했어요. \"우리… 내일도 같이 할래?\"",
    "피피가 활짝 웃었어요. 집에 가는 길, 아로의 발걸음이 **통통** 가벼웠어요."
  ],
  "questions": [
    "아이에게 어떤 일이 있었는지 조금 더 알려 주세요.",
    "그때 아이는 어떤 표정이었나요?",
    "아이가 그 일 뒤에 한 말이 있었나요?",
    "아이에게 가장 전하고 싶은 마음은 무엇인가요?",
    "아이가 좋아하는 놀이 중에 이야기에 넣고 싶은 게 있나요?"
  ],
  "guide": {
    "before_reading": { "intent": "처음 만나는 친구에게 말을 거는 용기를 함께 느껴 봐요.", "tips": ["아로의 표정 그림을 같이 가리켜 보세요.", "정답을 묻지 말고 아이의 말을 기다려 주세요."] },
    "prompts": [
      { "after_page": 4, "purpose": "감정 알아차리기", "text": "아로는 왜 혼자 앉았을까?", "parent_hint": "아이가 말하기 어려워하면 \"엄마도 처음엔 떨려\"라고 먼저 말해 주세요." },
      { "after_page": 7, "purpose": "행동 이어 보기", "text": "너라면 피피에게 뭐라고 말하고 싶어?", "parent_hint": null }
    ],
    "after_reading": { "bridge_question": "내일 어린이집에서 누구에게 인사해 볼까?", "if_undecided": "그림 속 피피처럼 먼저 웃어 주는 것도 좋은 인사예요." }
  }
}
```

### 실패 흉내 표시 추가

| 표시 (전하고 싶은 말이나 답변에 넣음) | 동작 |
|---|---|
| `[[PAGE_FAIL]]` | 결과에서 5쪽 그림만 FAILED |
| `[[AI_DOWN]]` | 그 다음 AI 호출 한 번이 `AI_UNAVAILABLE` |
| 기존 `[[FAIL]]`, `[[OUTPUT_REVISE]]`, `[[SENSITIVE]]`, `[[REVISE]]`, `[[CRISIS]]` | 그대로 |

## 2. 로컬 개발 편의

### 고정 인증번호 (local 전용)

프론트가 가입할 때마다 서버 로그에서 인증번호를 찾지 않도록:

```yaml
firstory:
  sms:
    local-fixed-code: "000000"     # application-local.yml.example에만
```

- **local 프로필에서만** 동작한다. 이 값을 읽는 빈 자체를 `@Profile("local")`로 등록하고, 다른 프로필에서 값이 설정돼 있으면 **기동 실패**
- 인증번호 생성 부분만 바꾸고, 해시 저장·만료·시도 횟수 규칙은 그대로
- 테스트: local 프로필이 아니면 이 설정이 무시가 아니라 기동 실패하는지 확인

### 프론트 개발 서버 연결

권장은 **프론트 개발 서버의 프록시**다. 운영 배포 구조(같은 주소에 `/api`)와 같아서 CORS와 쿠키 문제가 없다. 문서에 Vite 예시를 넣는다:

```js
// vite.config.js
export default {
  server: { proxy: { '/api': 'http://localhost:8081', '/swagger-ui': 'http://localhost:8081', '/v3': 'http://localhost:8081' } }
}
```

프록시를 못 쓰는 경우를 위해 `application-local.yml.example`에 `ALLOWED_ORIGINS: http://localhost:5173`도 넣고, 그때 주의점(쿠키 포함 요청, CSRF 헤더)을 문서에 적는다.

## 3. 전체 흐름 통합 테스트

`FullJourneyIntegrationTest` 하나에 실제 사용 순서를 처음부터 끝까지:

1. CSRF → 문자 인증 → 가입 → `GET /api/me`
2. 아이 프로필 저장
3. 동화 만들기: 상황 → 아이(저장된 프로필) → 전하고 싶은 말 → 인터뷰 시작 → 답변들 → CONFIRMING
4. 확인 → 생성 진행(pollOnce) → 미리보기 → 그림 1장 받기
5. 그림만 다시(3쪽) → 버전 2 → 버전 1로 전환 → 확정
6. 홈, 서재(UNREAD에 있음), 상세, 가이드
7. 읽기 시작 → 쪽 넘김 → 대화 버튼 → 완료 → 서재 READ로 이동
8. 로그아웃 → `GET /api/me` 401

## 4. 프론트 문서 (`docs/frontend/`)

### 4-1. `README.md`: 시작하기

- 준비물: Docker Desktop, JDK는 필요 없음(Gradle이 받음)
- 실행: `docker compose up -d` → `application-local.yml.example`을 복사해 `application-local.yml` → `./gradlew bootRun --args='--spring.profiles.active=local'` (Windows PowerShell 형식도)
- 주소: API `http://localhost:8081`, Swagger `/swagger-ui/index.html`
- 고정 인증번호 `000000`
- 자주 나는 문제: 포트 충돌, Docker 꺼짐, 프로필 없이 실행

### 4-2. `auth.md`: 인증 규칙

- 세션 쿠키 `FSESSION`, 로그인 유지 `FREMEMBER`
- CSRF: 처음과 로그아웃 뒤 `GET /api/auth/csrf`, 변경 요청에 `X-XSRF-TOKEN`
- 모든 요청 쿠키 포함, 401이면 로그인 화면
- 민감 작업(비밀번호, 휴대폰 변경, 탈퇴)은 현재 비밀번호 필요

### 4-3. `screens.md`: 화면별 API 순서

와이어프레임 화면 번호 기준 표. 각 화면마다: 들어올 때 부르는 API, 버튼별 API, 응답에서 볼 필드, 다음 화면.

포함할 화면: LND-01(없음), AUTH-01·02·03, ONB-01, HOME-01, CRT-01~06, ST-03·04·05, GEN-01, REV-01·02·04, 버전 목록, LIB-01·02, GDE-01, RD-01·02·03, MY-01

특히 자세히:
- **위기 신호 분기**: 모든 만들기 응답의 `safety.action`별 처리 (PROCEED, CHOOSE_TONE → ST-04, ASK_REVISE → 안내 후 다시 쓰기, BLOCK → ST-05, `helpKind`별 상담 기관은 프론트가 표시)
- **생성 진행(GEN-01)**: `GET /generation`을 3초 간격, 화면을 떠나도 생성은 계속됨, REVIEWING이면 REV-01, FAILED면 ST-03
- **그림**: `imageUrl`을 `<img src>`에 그대로. 쿠키가 자동으로 붙음. `imageStatus: FAILED`면 빈 자리
- **다시 만들기**: `regenerateRemaining`, 범위 3개, 그림만일 때 쪽 고르기

### 4-4. `errors.md`: 오류 코드 전체

`ErrorCode` 전부를 표로 (코드, HTTP, 메시지, 주로 나오는 API). **테스트로 문서와 enum이 어긋나지 않게 한다**: `ErrorCode.values()`의 모든 이름이 `errors.md`에 있는지 검사.

### 4-5. `fake-ai.md`: 가짜 AI와 진짜 AI의 차이

| 항목 | 가짜 (local 예시값) | 진짜 | 프론트가 대비할 것 |
|---|---|---|---|
| 인터뷰 응답 | 2초 | 수 초 | 로딩, 중복 클릭 방지 |
| 생성 | 약 2분 40초 | 3~4분 | 진행 화면, 나가도 된다는 안내 |
| 질문 수 | 1~5개 | 1~5개 | 고정 가정 금지 |
| 그림 실패 | `[[PAGE_FAIL]]` | 가끔 | 빈 자리 |
| AI 장애 | `[[AI_DOWN]]` | 가끔 | `AI_UNAVAILABLE`, `AI_BUSY` 안내 |
| 위기 신호 | 표시로 흉내 | 의미로 판단 | 흐름은 같음 |

표시 전체 목록과 넣는 위치도 적는다.

### 4-6. `firstory.http`: 바로 실행해 보는 요청 모음

IntelliJ HTTP Client / VS Code REST Client 형식. 3장의 전체 흐름 순서대로. CSRF 토큰을 응답 쿠키에서 꺼내 다음 요청 헤더에 넣는 방법 포함.

### 4-7. `status.md`: 지금 되는 것과 안 되는 것

- 되는 것: 위 화면 전부 (가짜 AI)
- 아직 안 되는 것: 진짜 AI, 실제 문자, S3, 배포, PM 확정 전 문구(`TODO(PM)` 목록)
- 바뀔 수 있는 것: 화면 문구 label들, 상담 기관(프론트), 알림 문구

## 5. 실제로 띄워서 확인 (반드시)

지금까지 TASK-05~07은 로컬 DB가 꺼져 있어서 실제 기동 확인을 못 했다. **이번엔 직접 한다.**

1. Docker Desktop이 꺼져 있으면 켠다
2. `docker compose up -d`, DB가 받을 준비가 될 때까지 기다린다
3. local 프로필로 bootRun (백그라운드)
4. `docs/frontend/firstory.http`의 요청을 curl로 같은 순서대로 실행하는 스크립트 `scripts/smoke-local.sh` (Git Bash에서 동작)를 만들고 돌린다
   - 쿠키 저장(`-c`, `-b`), CSRF 헤더, 고정 인증번호 사용
   - 생성은 local의 `stage-duration`을 이 스크립트 실행 동안 5s로 줄여 기다린다 (환경 변수로 덮어쓰기)
   - 그림 응답이 실제 PNG인지 (`Content-Type`, 파일 앞 바이트) 확인
5. 결과를 단계별로 보고한다. 실패하면 고치고 다시
6. 서버를 끈다

## 6. 마무리

- `CLAUDE.md`: 작업 문서 목록에 TASK-08, `docs/frontend/` 안내, 가짜 AI 설정
- 전체 테스트 통과 확인 후 push
- 태그: `git tag -a v0.1.0-frontend -m "프론트 전달용 첫 버전 (가짜 AI)"` → `git push origin v0.1.0-frontend`

---

## 완료 조건

- [ ] `./gradlew build` 통과
- [ ] test 프로필의 가짜 AI 동작이 바뀌지 않아 기존 테스트가 그대로 통과
- [ ] 고정 인증번호는 local 프로필에서만 동작하고, 다른 프로필에 설정하면 기동 실패
- [ ] `FullJourneyIntegrationTest` 통과
- [ ] `errors.md`가 ErrorCode와 일치 (테스트)
- [ ] **실제 기동 + smoke 스크립트로 전체 흐름 통과** (결과 보고)
- [ ] `docs/frontend/` 7개 문서
- [ ] 태그 `v0.1.0-frontend` push
