# FIRSTORY Backend

부모가 아이와 나누기 어려운 이야기를 AI 인터뷰로 정리해 10쪽짜리 맞춤 동화와 대화 가이드를 만들어 주는 웹 서비스의 백엔드.
AI 생성은 별도 Python 서비스(LangGraph)가 맡고, 이 저장소는 회원, 아이, 동화, 읽기 기록, 알림 같은 도메인 데이터와 AI 서비스 호출을 맡는다.

## 기술 스택

- Java 21, Spring Boot 4.1.1, Gradle (Groovy DSL)
- Hibernate 7, Jakarta Persistence 3.2 (Spring Boot 4.1이 관리)
- PostgreSQL 16 + Flyway (버전은 Spring Boot BOM이 관리. 직접 지정하지 않는다)
- Spring Security 7 + Spring Session JDBC (세션은 PostgreSQL에 저장. Redis는 쓰지 않는다)
- Lombok

## 명령어

```bash
docker compose up -d          # 로컬 PostgreSQL
./gradlew build               # 컴파일 + 테스트
./gradlew test
./gradlew bootRun --args='--spring.profiles.active=local'   # local 프로필 필수 (아래 참고)
```

빌드 결과로 커밋할 때는 `./gradlew build && git commit ...`처럼 `&&`로 잇고 **빌드 명령에 파이프(`| tail`, `| grep` 등)를 붙이지 않는다.** 파이프가 있으면 종료 코드가 마지막 명령 것이 되어 `&&`가 빌드 실패를 잡지 못한다.

`SmsSender` 구현은 아직 `LoggingSmsSender`(`local`, `test`, `dev` 프로필 전용)뿐이다. 프로필 없이 띄우면 `SmsSender` 빈이 없어 기동이 실패한다 (의도된 동작. 실제 문자 대행사 연동은 아직 없다).

### 로컬 실행 (포트가 겹치는 개발 PC)

로컬에 PostgreSQL(5432)이나 다른 앱(8080)이 이미 떠 있으면 `local` 프로필로 띄운다. DB 55432, 서버 8081을 쓴다.

```bash
cp .env.example .env                                                        # POSTGRES_PORT=55432
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
docker compose up -d                                                        # .env를 읽어 55432로 띄움
./gradlew bootRun --args='--spring.profiles.active=local'
```

`application-local.yml`은 `firstory.ai.mode: fake`라서 AI 서버 없이 뜬다(아래 "AI 호출 규칙"). 가짜 AI는 local 예시값(`firstory.ai.fake.*`)으로 진짜처럼 느리다: 인터뷰 2초, 글 검사 1초, 생성 단계당 40초(약 2분 40초), 질문 수는 동화마다 1~5개. 인증번호는 `firstory.sms.local-fixed-code`로 항상 `000000`이다. 가짜 AI가 만든 그림 파일은 `./data/storage`(`STORAGE_LOCAL_ROOT`)에 쌓인다. `data/`는 커밋하지 않는다. 지워도 되지만 지우면 이미 만든 동화의 그림은 404가 난다.

`.env`와 `application-local.yml`은 커밋하지 않는다. 설정을 바꾸면 `.example` 파일도 함께 고친다.

전체 흐름 실제 확인(smoke): 서버를 `FIRSTORY_AI_FAKE_STAGE_DURATION=5s ./gradlew bootRun --args='--spring.profiles.active=local'`로 띄우고 다른 창에서 `bash scripts/smoke-local.sh`(Git Bash, curl만 필요). `docs/frontend/firstory.http`, `FullJourneyIntegrationTest`와 같은 순서로 35단계를 돌고 그림이 실제 PNG인지도 본다. 진짜 AI 서버와 붙였으면(`FIRSTORY_AI_MODE=http`, AI 서버의 `FIRSTORY_OUTPUT_ROOT`와 `STORAGE_LOCAL_ROOT`를 같은 폴더로) `AI_MODE=http bash scripts/smoke-local.sh`로 돌린다. 가짜 AI만 만드는 샘플 제목과 `(다시 n)`은 확인하지 않는다. 진짜 AI는 인터뷰 시작에서 질문 없이 바로 CONFIRM을 줄 수 있어 smoke는 질문 0~5개를 모두 통과시킨다. API 흐름을 바꾸면 세 곳을 함께 고친다. Windows에서 curl로 한글 본문을 보낼 때는 인자가 아니라 파일(`--data-binary @file`)로 보낸다(코드 페이지 때문에 깨진다).

### 개발 서버 (dev 프로필, Railway)

`application-dev.yml`(커밋함). 루트 `Dockerfile`(Gradle bootJar → temurin 21 JRE, root 아닌 사용자)로 빌드하고 `SPRING_PROFILES_ACTIVE=dev`로 띄운다. Swagger 켬, 가짜 AI(local 예시값과 같은 속도), 고정 인증번호 `000000`, `LoggingSmsSender`, `COOKIE_SECURE` 기본 true. DB 주소와 키 세 개(`PHONE_AES_KEY`, `PHONE_HMAC_KEY`, `REMEMBER_ME_KEY`)는 기본값 없이 받아 비면 기동하지 않는다. 포트는 `SERVER_PORT` → `PORT`(Railway) → 8080. 헬스체크는 `GET /api/health`(로그인 불필요, `{"status":"ok"}`). 환경 변수 전체는 `docs/deploy/railway.md`. 환경 변수를 추가하면 그 표도 고친다.

### 프론트 전달 문서 (`docs/frontend/`)

프론트 담당자가 이 문서만 보고 화면을 붙인다. README(시작하기), auth, screens(화면별 API), errors, fake-ai, firstory.http, status. API·오류 코드·가짜 AI 동작을 바꾸면 해당 문서도 고친다. `errors.md`의 표는 `FrontendErrorsDocTest`가 `ErrorCode`(이름, HTTP 상태, 메시지)와 비교하므로 코드를 추가하면 문서에 행을 넣어야 빌드가 통과한다.

### Swagger (local, dev 프로필 전용)

`local` 프로필로 띄우면 http://localhost:8081/swagger-ui/index.html 에서 열린다(API 문서 JSON은 `/v3/api-docs`). dev(개발 서버)도 켜진다. 다른 프로필(운영, test)에서는 꺼져 있어 404가 난다. 켜고 끄는 설정은 `application.yml`의 `springdoc`과 그 아래 `on-profile: local | dev` 문서에 있다.

로그인은 세션 쿠키, 상태를 바꾸는 요청은 CSRF 헤더가 필요해서 순서가 있다.

1. `GET /api/auth/csrf`를 먼저 실행한다. 브라우저에 `XSRF-TOKEN` 쿠키가 생긴다.
2. POST/PUT/PATCH/DELETE는 Swagger UI가 이 쿠키 값을 `X-XSRF-TOKEN` 헤더로 자동으로 붙인다(`springdoc.swagger-ui.csrf.enabled=true`). 따로 입력할 것은 없다.
3. 가입하려면 `send-code` → `verify`(local은 고정 인증번호 `000000`. 설정을 지우면 서버 로그의 `[SMS] 010-****-1234 ← [FIRSTORY] 인증번호 123456`에서 확인) → `signup` 순서로 같은 브라우저에서 실행한다. 가입하면 바로 로그인된다.
4. 로그인하면 `FSESSION` 쿠키로 인증된다. 이후 `/api/me`, `/api/children` 등을 바로 실행할 수 있다.
5. 동화 만들기(CRT-01~05)는 `POST /api/stories` → `situation` → `child` → `message` → `interview/start` → `interview/answer` 순서다. local은 FakeAiClient라 동화마다 정해진 1~5번째 답변이나 `{"skip": true}`에서 인터뷰가 끝난다(CONFIRMING). 글에 `[[SENSITIVE]]`, `[[REVISE]]`, `[[CRISIS]]`를 넣으면 그 위기 신호로 판단한다.
   이어서 `confirm` → `generation`(local 예시값은 단계당 40초라 약 2분 40초면 REVIEWING. `FIRSTORY_AI_FAKE_STAGE_DURATION=5s`로 띄우면 약 25초) → `preview` → 그림 API(`imageUrl`을 브라우저에서 연다) → `regenerate` → `current-version` → `approve` 순서로 확정까지 해 볼 수 있다. 전하고 싶은 말이나 답변에 `[[FAIL]]`을 넣으면 첫 생성이 실패하고(`retry`로 다시 시도), `[[OUTPUT_REVISE]]`는 결과 위기 신호로 실패, `[[PAGE_FAIL]]`은 5쪽 그림만 실패, `[[AI_DOWN]]`은 다음 AI 호출 한 번이 `AI_UNAVAILABLE`이다(`docs/frontend/fake-ai.md`).
6. 로그아웃하면 `XSRF-TOKEN`도 지워진다. 다시 쓰려면 1번부터 한다. 403 `FORBIDDEN`이 나오면 대부분 1번을 빠뜨린 것이다.

요청·응답 DTO의 `Map` 필드에는 `@Schema(example = "{...}")`를 단다(없으면 Swagger가 `additionalProp1`을 보여 준다. `OpenApiDocsTest`가 검사한다). 받지 않는 enum 값이 있으면 `allowableValues`로 줄이고, 형식이 정해진 문자열(휴대폰, 생년월 등)에는 실제로 통과하는 `example`을 단다.

API를 추가할 때는 `@Operation(summary = "한국어 요약")`과 `@ApiErrorCodes({ErrorCode...})`를 단다. `OpenApiConfig`가 `ErrorCode`의 상태와 메시지로 오류 응답 설명을 만든다. 로그인이 필요한 컨트롤러는 클래스에 `@ApiErrorCodes(ErrorCode.UNAUTHORIZED)`를 달고, 상태를 바꾸는 요청의 403(CSRF)은 자동으로 붙는다. 성공 상태가 200이 아니면 `@ResponseStatus`로 적는다(`ResponseEntity`로는 문서에 나타나지 않는다).

## 패키지 구조 (도메인별)

```
com.firstory
├── FirstoryApplication   하위 패키지를 모두 스캔하도록 루트에 둔다
├── global                config, entity(BaseTimeEntity), error, crypto, converter, security, sms, ai(AI 서비스 호출), storage(파일 저장소)
└── domain
    ├── auth              문자 인증, 가입, 로그인, 문자 발송 기록
    ├── user              회원, 동의, 내 정보
    ├── child             아이 프로필, 성향
    ├── story             동화, 만들기 입력, 인터뷰, 상태
    ├── generation        생성 실행, 버전, 페이지
    ├── guide             대화 가이드, 대화 질문
    ├── reading           읽기 세션, 대화 버튼 기록
    ├── safety            위기 신호
    └── notification      문자 알림
```

각 도메인(`com.firstory.domain.{도메인}`) 안은 계층별로 나눈다.

| 패키지 | 담는 것 |
|---|---|
| `entity` | 엔티티, enum, 값 객체(record), 정책 클래스(`ConsentPolicy`, `Interests` 등) |
| `repository` | Spring Data JPA Repository |
| `service` | 트랜잭션 경계, 도메인 흐름 조합 |
| `controller` | REST API |
| `dto` | 요청·응답 객체, AI 서비스 연동 매핑 |

클래스가 아직 없는 계층의 패키지는 미리 만들지 않는다. 테스트도 같은 패키지 구조를 따른다(공용 픽스처는 `com.firstory.support`).

## 의존성 규칙 (Spring Boot 4에서 자주 꼬이는 부분)

- Spring Boot 4는 자동 설정이 기술별 모듈로 쪼개져 있다. **Flyway는 `flyway-core`만 넣으면 자동 설정이 동작하지 않는다.** 반드시 `spring-boot-starter-flyway`를 쓴다.
- `flyway-core`, `flyway-database-postgresql`, Testcontainers, Hibernate 등 BOM이 관리하는 라이브러리에 **버전을 직접 적지 않는다.** 버전이 섞이면 Flyway가 PostgreSQL을 인식하지 못하는 오류가 난다.
- 의존성을 바꾼 뒤에는 `./gradlew dependencyInsight --dependency flyway`로 flyway 모듈들의 버전이 모두 같은지 확인한다.
- 예외: springdoc-openapi는 BOM에 없어서 버전을 적는다(`3.1.1`). springdoc 3.1.x가 Spring Boot 4.1 기준이다. Spring Boot를 올릴 때 springdoc 릴리스 노트에서 호환 버전을 다시 확인한다.
- 예외: AWS SDK(S3)도 BOM에 없어서 AWS BOM(`software.amazon.awssdk:bom`)에만 버전을 적고 모듈(`s3`)에는 적지 않는다.

## 스키마 규칙

- 스키마의 기준은 `src/main/resources/db/migration`의 Flyway SQL이다. 엔티티는 SQL에 맞춘다.
- `spring.jpa.hibernate.ddl-auto=validate`. 엔티티와 SQL이 다르면 앱이 뜨지 않아야 한다.
- 이미 적용된 마이그레이션 파일은 절대 수정하지 않는다. 변경은 `V2__설명.sql`처럼 새 파일로 추가한다.
- ERD에 없는 테이블이나 컬럼을 임의로 추가하지 않는다. 필요하면 먼저 사용자에게 묻는다.

## JPA 규칙 (중요)

- 연관관계는 **단방향 `@ManyToOne(fetch = LAZY)`만** 쓴다. 엔티티에 `@OneToMany` 컬렉션을 두지 않는다. 자식 목록은 Repository에서 조회한다.
  - 예외: 값 타입 컬렉션 `@ElementCollection`(예: `Child.traits`)은 허용.
- setter 금지. `@NoArgsConstructor(access = PROTECTED)` + 정적 팩토리 메서드 + 의도가 드러나는 변경 메서드(`confirm()`, `showVersion()` 등)를 쓴다.
- enum은 `@Enumerated(EnumType.STRING)`.
- 시간은 `java.time.Instant` ↔ `timestamptz`. 모든 엔티티는 `BaseTimeEntity`(created_at, updated_at)를 상속한다.
- JSON 컬럼은 `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"`.
- 소프트 삭제 엔티티(User, Child, Story)는 `deleted_at` + `@SQLRestriction("deleted_at IS NULL")`.
- `spring.jpa.open-in-view=false`. 지연 로딩은 서비스 트랜잭션 안에서 끝낸다.
- 다른 사용자의 데이터를 조회하지 않도록 수정·삭제·상세 조회는 항상 `findByIdAndUserId`처럼 소유자 조건을 함께 건다.

## 도메인 규칙

- 동화 상태는 반드시 `Story.changeStatus()`로만 바꾼다. 허용된 전이는 `StoryStatus`에 정의되어 있다.
- 버전은 지우지 않는다. 되돌리기와 앞으로 가기는 `Story.currentVersion`만 바꾼다. 버전 전환은 다시 만들기 횟수에 포함하지 않는다.
- 다시 만들기(`Story.requestRegeneration`)는 `REVIEWING`에서만 할 수 있고 `regenerateCount`를 1 올린다. 최대 횟수는 `firstory.policy.max-regenerations`(1, 기획 확정). 실패 후 다시 시도(`Story.retry`, `FAILED → REQUESTED`)는 다시 만들기 횟수에 포함하지 않는다. 한도를 다 써도 재시도는 할 수 있다.
- `PhoneNumber.toString()`은 번호를 가린다(`PhoneNumber[***]`). 로그에 실수로 찍혀도 번호가 남지 않게 하기 위함이다. 번호가 필요하면 `digits()`나 `display()`를 명시적으로 쓰고, 그 값은 로그에 남기지 않는다.
- 위기 신호는 AI가 4단계(`SafetyLevel`: OK, SENSITIVE, REVISE, CRISIS)로 판단하고, 대응은 `SafetyPolicy`(컴포넌트)가 정한다. OK는 진행, SENSITIVE는 분위기 선택(ST-04, tone을 이미 골랐으면 진행), REVISE는 `firstory.policy.safety-revise-limit`(2)번째 전까지 다시 쓰기 안내·그 횟수째에 차단, CRISIS는 1번만에 차단(ST-05). 기록(`SafetyFlag`)은 REVISE, CRISIS만 하고, 횟수와 `occurrenceNo`는 동화 단위·단계별로 센다(`countByStoryIdAndLevel`). 차단 판단에는 REVISE 횟수만 쓴다.
- 동화 쪽수는 `firstory.story.page-count`(10)다. DB와 엔티티는 쪽 번호 1 이상만 검사하고 상한은 이 설정값으로 본다.
- 아이 프로필은 "다음에도 이 아이 정보로 만들기"를 체크했을 때만 저장한다. 동화는 항상 `StoryInput.childSnapshot`을 기준으로 만든다.
- 동화 만들기에서 아이 정보는 `ChildProfileResolver.resolve` 하나로 처리한다(저장 여부 다섯 경우). snapshot은 항상 화면의 최종 입력값으로 만들고 프로필에서 읽지 않는다.
- 다른 회원의 아이는 403이 아니라 404 `CHILD_NOT_FOUND`로 응답한다(존재 여부를 드러내지 않는다).
- 아이 프로필 삭제는 `StoryRepository.detachChild`로 동화 연결을 먼저 끊고 소프트 삭제한다. `detachChild`는 영속성 컨텍스트를 비우므로 Child를 다시 읽어서 지운다.
- 날짜(오늘, 이번 달, 개월 수)는 `ClockConfig.today(clock)`로 서비스 기준 시간대(Asia/Seoul)에서 센다. 시각(Instant)은 UTC 그대로 둔다.

## AI 입력 규칙

- 값 이름은 AI 저장소(`schemas.py`의 `StoryInputContext`)와 같은 단어를 쓴다. DB와 Java는 대문자(`name()`), AI로 보낼 때는 소문자(`AiValue.aiValue()`)로 바꾼다. 이름을 따로 짓고 변환표를 두지 않는다. AI로 보내는 enum은 `AiValue`를 구현한다.
- 화면 문구(label)는 enum 한곳에만 두고 선택지 API(`GET /api/children/options` 등)로 내려준다.
- "기타"(OTHER)를 고르면 직접 입력이 필수이고(`CUSTOM_VALUE_REQUIRED`), 고르지 않았으면 직접 입력은 버린다. 길이는 `CustomText`로 검사한다(`CUSTOM_VALUE_TOO_LONG`, 메시지의 `{n}`은 항목별 글자 수). 성향 20자, 좋아하는 것 한 개당 20자, 상황 분류·반응 30자, 상황 설명 60자.
- 좋아하는 것은 목록(`interestCodes`)과 직접 입력(`customInterests`)을 합쳐 5개까지다(`Interests.normalize`).
- 앞으로 겪을 일(UPCOMING)에는 반응과 감정을 고를 수 없다(`MOMENT_ONLY_FOR_PAST`).
- AI 입력은 `AiContextMapper.toContext(StoryInput)`으로만 만든다. 좋아하는 것 직접 입력이 있으면 `interests`에 `"other"`를 더하고 `custom_interest`에 `", "`로 이어 붙인다(AI가 한 칸만 받는다).
- `story_inputs.child_snapshot`의 형식이 TASK-04에서 바뀌었다(`interests` → `interestCodes` + `customInterests`, `customTrait` 추가). 이전 형식 데이터는 로컬 개발 데이터뿐이라 호환 처리하지 않는다. 로컬 DB에 옛 동화가 있으면 지우고 다시 만든다.

## AI 호출 규칙

- AI 서비스(FastAPI) 스펙은 `docs/FIRSTORY-AI-API-SPEC-v0.3.md`. 호출은 `global.ai.AiClient` 하나로 한다.
- `firstory.ai.mode`로 구현을 고른다(`AiClientConfig`). `http`(기본)는 `HttpAiClient`(RestClient, `X-Internal-Token`, 연결 3초·응답 35초), `fake`는 `FakeAiClient`. local, test 프로필은 `fake`. `http`인데 `AI_INTERNAL_TOKEN`이 비어 있으면 기동하지 않는다.
- `FakeAiClient`는 고정 동작이고 속도와 질문 수만 `firstory.ai.fake.*`(`FirstoryProperties.Ai.Fake`)로 바꾼다. **test 프로필은 기본값(지연 0, `stage-duration` 0, `turns` 2)이라 아래 동작 그대로다. 테스트가 기대는 동작을 바꾸지 않는다.**
  - 위기 신호는 글에 `[[SENSITIVE]]`, `[[REVISE]]`, `[[CRISIS]]`가 있으면 그 level, 없으면 OK.
  - 인터뷰는 질문 1 → 답변 `turns`번까지 다음 질문 → 그다음 답변이나 skip이면 CONFIRM (`turns` 2면 3번째 답변). `turns` 0이면 thread_id 해시로 질문 1~5개. 질문 문장은 `fake-ai/sample-story.json` 순서대로, 답변 카드는 같은 파일의 `questionChoices`(5번째 질문은 카드 없음).
  - 생성은 design_story → write_story → illustrate → render → REVIEWING (글 다시는 write_story부터, 그림만 다시는 illustrate_only → render). `stage-duration` 0이면 `status`를 부를 때마다 한 단계씩, 0보다 크면 시작 후 지난 시간(`Clock`) / `stage-duration`으로 단계를 정한다. REVIEWING이 될 때 `fake-ai/sample-story.json`(제목, 10쪽 글, 가이드)으로 결과를 만들고 실제 PNG(쪽 1920×1088, 표지 1024×1024, 진짜 AI와 같은 크기)를 `FileStorage`에 쓴다. 그림만 다시(일부 쪽)는 이전 표지 key. 글 자리(`textPosition`)는 3의 배수 쪽만 TOP(AI `mock.py`와 같다). 다시 만들기면 제목 뒤에 `(다시 n)`.
  - 전하고 싶은 말이나 인터뷰 답변의 표시: `[[FAIL]]` 첫 생성 실패, `[[OUTPUT_REVISE]]` 첫 생성 결과의 safety가 REVISE, `[[PAGE_FAIL]]` 첫 생성 결과의 5쪽 그림만 failed, `[[AI_DOWN]]` 위기 신호 검사에서 처음 본 글이면 다음 AI 호출 한 번이 `AI_UNAVAILABLE`(생성 진행 확인 `status`, `result`는 세지 않는다).
  - 지연(`interview-delay`, `safety-delay`)은 `Thread.sleep`이다. local 전용이라 허용한다. thread 상태는 메모리에만 있어 서버를 다시 띄우면 진행 중인 인터뷰와 생성은 이어갈 수 없다. 테스트에서는 `failWith(ErrorCode)`로 장애를, `misdirectOutput()`으로 다른 동화 경로를 흉내 내고(`clearFailure()`로 둘 다 되돌린다), `answerCalls`, `statusCalls`, `lastBaseCheckpointId`로 호출을 본다.
- **DB 트랜잭션 안에서 AI를 부르지 않는다**(응답이 35초까지 걸려 커넥션을 붙잡는다). ① 트랜잭션으로 조회·검증 → ② 트랜잭션 밖에서 AI 호출 → ③ 새 트랜잭션으로 저장. ③에서 동화를 `StoryRepository.findByIdForUpdate`로 다시 읽어 상태가 바뀌었으면 `INVALID_STATUS_TRANSITION`. AI를 부르는 서비스 메서드에는 `@Transactional`을 달지 않고 `TransactionTemplate`을 쓴다. `AiClient` 구현은 트랜잭션 안에서 불리면 `IllegalStateException`을 던진다(`TransactionGuard`).
- 문자 발송(`SmsSender.send`)도 같은 이유로 트랜잭션 밖에서 한다.
- 인터뷰 답변 카드(`choices`)는 AI 응답을 화면에 넘기기만 하고 저장하지 않는다. `InterviewReply`가 빈 것·겹치는 것을 빼고 `MAX_CHOICES`(4)개까지 남긴다. 다시 쓰기 안내(ASK_REVISE)로 같은 질문을 다시 줄 때는 빈 목록이다.
- AI 오류는 `AI_UNAVAILABLE`(연결 실패, 타임아웃, 5xx, 422, 응답 형식 오류), `AI_BUSY`(409 THREAD_BUSY), `AI_STATE_MISMATCH`(409 INVALID_STATE, 404 THREAD_NOT_FOUND)로 바꾼다. AI 호출이 실패하면 아무것도 저장하지 않는다.
- **AI 요청·응답 본문을 로그에 남기지 않는다.** 오류 로그에는 thread_id, 상태 코드, AI 오류 code만 남긴다. AI 응답 record(`InterviewReply`)와 요청·응답 DTO는 `toString`에서 내용을 가린다.

## 생성 흐름 (CRT-06 ~ REV-04)

- `POST /confirm`(CONFIRMING → REQUESTED)이 `AiClient.generate`를 부르고 `GenerationRun`(INITIAL, ALL)을 만든다. AI는 바로 받아들이고 뒤에서 만든다. 생성 전 확인에서 고쳤을 수 있어 지금 입력 전체(`AiContextMapper.toContext`)와 `interviewSummary`를 함께 보낸다. AI가 인터뷰 결과와 합친다(상황을 안 고쳤으면 인터뷰가 보충한 moment를 지킨다).
- 생성 전 확인(CRT-06)에서 아이·상황·전하고 싶은 말(`DRAFT`, `CONFIRMING`)과 "이번 이야기에서 나누고 싶은 것"(`PUT /summary`, `CONFIRMING`만, 1~300자)을 고칠 수 있다. 인터뷰는 다시 하지 않는다. 전하고 싶은 말은 `StoryInput.writeMessage`로 그 칸만 바꾼다(`writeParent`는 인터뷰가 채운 response·concern·방향까지 덮어쓴다). `interviewSummary`는 AI가 만든 부모용 문장이고 부모가 고치면 그 문장이 된다.
- **진행은 `GenerationPoller`가 AI에 묻는다.** `firstory.generation.poll-interval`(5s)마다 RUNNING 실행을 `FOR UPDATE SKIP LOCKED`로 최대 20개 짧게 잡고, 실행마다 트랜잭션 밖에서 `AiClient.status`를 부른다. 저장할 때는 실행을 `findByIdForUpdate`로 다시 잠가 아직 RUNNING일 때만 바꾼다. 그래서 서버가 여러 대여도 같은 실행을 두 번 저장하지 않는다. test 프로필은 `poll-enabled: false`이고 테스트가 `pollOnce()`를 직접 부른다.
  - AI RUNNING: 노드로 단계를 정해(`GenerationStage`, 모르는 노드는 그대로) `run.progress`, 스토리는 1~2단계 GENERATING_TEXT, 3~4단계 GENERATING_IMAGES (`Story.advanceGenerationTo`가 허용된 전이를 차례로 밟는다. 뒤로 가지 않는다)
  - AI REVIEWING: `AiClient.result` → `GenerationResultRecorder`
  - AI FAILED, 시작 후 `firstory.generation.timeout`(15m) 지남(TIMEOUT): 실행 FAILED, 스토리 FAILED
  - AI 연결 실패: 아무것도 바꾸지 않고 다음 주기에 다시 (결국 timeout이 처리). 실행 하나의 실패가 다른 실행을 막지 않는다
- **진행 조회(`GET /generation`)는 DB만 읽는다. AI를 부르지 않는다.**
- `GenerationResultRecorder`(한 트랜잭션): 파일 key가 모두 `stories/{storyId}/` 아래인지(아니면 INVALID_OUTPUT_PATH), 쪽 수가 `page-count`인지(INVALID_PAGE_COUNT), 결과 위기 신호(아래)를 본 뒤 버전(`parentVersion` = 실행의 기준 버전), 페이지, 가이드(`text` → `question`)를 만들고 실행 SUCCEEDED, 스토리 REVIEWING, 새 버전을 보여준다.
- 다시 만들기(`regenerate`)는 CHARACTER_STORY, TEXT, IMAGES(`pages`로 쪽 선택, 비면 전체)만. 메모는 300자, 위기 신호를 먼저 검사한다. AI가 실패하면 실행도 횟수도 저장하지 않는다. AI에는 **현재 버전의 checkpointId**를 기준으로 보낸다(되돌린 버전에서 갈라진다).
- 그림만 다시는 바뀌지 않은 쪽이 이전 실행의 파일 key를 그대로 쓴다. 글 다시 만들기도 AI가 장면이 바뀐 쪽만 새로 그려, 나머지 쪽과 캐릭터 시트는 이전 실행 key다. 파일을 복사하지 않는다.
- 다시 시도(`retry`, FAILED → REQUESTED)는 마지막 실패 실행과 같은 범위·쪽·기준 버전으로 RETRY 실행을 만든다. 다시 만들기 횟수에 넣지 않는다. 마지막 실패가 결과 위기 신호(`OUTPUT_UNSAFE`)면 AI 그래프는 부모 검토에서 멈춰 있어 `AiClient.retry`가 409이므로 `AiClient.regenerate`를 부른다(범위는 실패한 실행의 것, ALL이면 CHARACTER_STORY, 기준은 그 실행의 기준 버전 checkpointId, 첫 생성이면 null).
- AI 결과의 가이드는 AI 저장소 `nodes/render.py`의 04-guide.json 그대로(`synopsis`, `before_reading{intent, tips}`, `prompts[{after_page, purpose, text, parent_hint}]`, `after_reading{bridge_question, if_undecided}`). `text` → `guide_prompts.question`, `seq`는 받은 순서대로 1부터, `parent_hint`와 `if_undecided`는 없을 수 있다. `synopsis`(동화 줄거리)는 `reading_guides.synopsis`에 저장하고 동화 상세(`StoryDetailResponse.synopsis`)로 지금 보여 주는 버전의 것을 내린다. 없거나 빈 문자열이면 null.
- 확정(`approve`)은 Spring이 먼저 저장하고(COMPLETED, confirmedAt) 트랜잭션 밖에서 `AiClient.approve`를 부른다. AI가 실패해도 확정은 유지한다.

## 파일 저장소와 그림 API

- 파일은 `global.storage.FileStorage`로만 읽고 쓴다. `firstory.storage.type`(`STORAGE_TYPE`)으로 고른다(`StorageConfig`). 그림 API 주소와 동작은 어느 쪽이든 같다.
  - `local`(기본): `LocalFileStorage`(`firstory.storage.local-root`, 기본 `./data/storage`, test는 `build/test-storage`). 진짜 AI 서버와 같은 컴퓨터에서 같은 폴더를 볼 때만 쓴다.
  - `s3`: `S3FileStorage`. `STORAGE_S3_BUCKET`(필수, 비면 기동하지 않는다), `STORAGE_S3_REGION`(기본 `ap-northeast-2`). key가 그대로 객체 key다. AI 서버도 같은 버킷에 쓴다(AI 저장소 `FIRSTORY_STORAGE=s3`). 접근 키는 설정 파일에 두지 않고 AWS 기본 방식(`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` 환경 변수, EC2는 IAM 역할)으로 찾는다. 버킷은 공개하지 않는다(그림은 그림 API로만 내려준다).
- key 형식은 `stories/{storyId}/runs/{runNo}/images/page-01.png`, `.../images/sheet-{role}-{n}.png`, `.../images/cover.png` (`StoryFiles`). AI에 넘기는 `output_prefix`도 `StoryFiles.runPrefix`로 만든다.
- key 검사는 `StorageKeys.validate` 하나로 한다(`..`, 빈 칸, 절대 경로, 역슬래시, `:` 거부. AI 저장소 `storage.py`와 같은 기준). `LocalFileStorage`는 `normalize()`한 경로가 루트 밖이면 한 번 더 거부한다.
- 그림 API(`/api/stories/{id}/versions/{versionId}/pages/{pageNo}/image`, `/character-sheet`, `/cover`): 내 동화(`findByIdAndUserId`) → 그 동화의 버전 → 그 버전의 쪽 순서로 확인하고 하나라도 아니면 404. **파일 위치는 DB에 저장된 key로만 찾는다.** key가 `stories/{storyId}/`로 시작하는지 한 번 더 본다. 헤더는 `image/png`, `nosniff`, `private, max-age=3600`, `inline`. 오류를 JSON으로 내려야 해서 `produces`를 걸지 않는다.
- **응답 어디에도 파일 key나 서버 경로를 넣지 않는다.** 주소는 DB id로만 만든다(`StoryImageUrls`).

## 알림 규칙

- `NotificationService.notify(runId, type)`는 **트랜잭션 밖에서** 부른다. 최신 SMS_NOTIFY 동의가 활성일 때만, 같은 (실행, 종류)가 없을 때만 PENDING 저장 → `SmsSender.send` → SENT/FAILED. 예외를 던지지 않는다(알림 실패가 생성 흐름을 깨뜨리지 않는다).
- STORY_STARTED는 처음 만들기(INITIAL)에만, STORY_COMPLETED와 STORY_FAILED는 모든 실행에. 확정 때는 보내지 않는다(PRD 33). 문구는 `NotificationTemplate`(code가 `template_code`).
- 문자에 아이 정보, 동화 제목, 부모 입력을 넣지 않는다.

## 서재와 함께 읽기 (HOME-01, LIB-01~02, GDE-01, RD-01~03)

- 서재(`GET /api/stories`) filter: ALL(BLOCKED 뺀 전부), CREATING(DRAFT~GENERATING_IMAGES), REVIEWING, UNREAD(COMPLETED + 함께 읽은 적 없음), READ(COMPLETED + 읽은 적 있음), FAILED (`LibraryFilter`). 정렬은 `updated_at DESC, id DESC`, size 1~50(기본 20).
- **BLOCKED 동화는 서재와 홈 어디에도 보이지 않는다.** 상세(`GET /api/stories/{id}`)에서는 상태 그대로 보여준다(차단 화면 재진입용).
- **서재·홈 목록은 현재 버전과 아이를 페치 조인한다** (`StoryRepository.findLibrary`). 카드의 상황 분류(`categoryLabel`)는 Story에서 StoryInput으로 가는 연관이 없어 목록의 동화 id로 한 번에 읽는다(`LibraryService.cards`). 목록 크기와 무관하게 목록 1번 + 개수 1번 + 상황 분류 1번. 카드에 연관 엔티티를 더 쓰면 조인이나 한 번에 읽는 쿼리를 함께 늘린다.
- 카드의 `title`, `coverUrl`은 버전이 있을 때만. `coverUrl`은 표지 그림 API(`story_versions.cover_key`, AI가 제목을 넣어 그린 정사각형)이고 표지가 없는 버전(이전 버전, 표지 실패)은 캐릭터 시트 그림 API(`StoryImageUrls.coverOrSheet`). 미리보기·상세·읽기도 같다. `label`은 `Story.libraryLabel()`, `categoryLabel`은 `StoryInput.categoryLabel()`(기타면 직접 입력한 분류).
- 삭제(`DELETE /api/stories/{id}`)는 생성 중(REQUESTED, GENERATING_TEXT, GENERATING_IMAGES)을 뺀 모든 상태에서. 소프트 삭제라 목록·상세·가이드·읽기·그림 API가 모두 404. 입력 원문과 그림 파일의 실제 파기는 아직 없다(TODO(PM) PRD 36).
- 가이드는 **REVIEWING·COMPLETED**에서 지금 보여 주는 버전의 것(미리보기에서 동화 설명과 가이드를 보여 주려고). 읽기는 **COMPLETED일 때만**이고 확정된 현재 버전을 쓴다. 아니면 `INVALID_STATUS_TRANSITION`.
- 읽기 세션: 시작 `201`, 이어 읽기는 가장 최근의 끝나지 않은 세션(없으면 `204`), 쪽 넘김은 1~page-count(`PAGE_OUT_OF_RANGE`), 끝난 세션의 쪽 넘김은 `INVALID_STATUS_TRANSITION`. 끝(complete)은 두 번 불러도 `204`이고 `Story.firstReadCompletedAt`은 처음 한 번만 남는다.
- **세션 id로 들어오는 API는 세션 → 동화 → 회원까지 확인한다** (`ReadingSessionRepository.findOwnedForUpdate`, 다른 회원이면 404 `READING_SESSION_NOT_FOUND`). 세션 행을 잠가 같은 세션의 동시 요청을 차례로 처리한다.
- 대화 버튼(`TALKED`, `SKIPPED`)은 그 세션 버전의 가이드 질문일 때만(`GuidePromptRepository.findInVersion`, 아니면 404 `PROMPT_NOT_FOUND`). 같은 질문에 다시 누르면 마지막 값으로 바꾼다(`DialogueAction.change`). North Star(대화 발생)는 TALKED 수로 센다.

## 위기 신호 처리 흐름

- 글을 받는 곳(CRT-01~04 폼, 생성 전 확인의 나누고 싶은 것, 인터뷰 답변, 다시 만들기 메모)은 `SafetyGuard.check`를 부른다. 빈 글은 빼고, 남은 글이 없으면 AI를 부르지 않는다. 버려질 직접 입력(OTHER를 고르지 않았을 때)은 검사하지 않는다.
- 형식 검증(`StoryInput.validateSituation`, `ChildProfileRequest.toProfile` 등)은 AI 호출 전에 한다. 형식 오류는 AI를 부르지 않고 400.
- 대응: OK → PROCEED. SENSITIVE → `markToneRequired()`, tone이 있으면 PROCEED 아니면 CHOOSE_TONE(저장하고 ST-04). REVISE → `SafetyFlag` 저장 후 `SafetyPolicy.decide`로 ASK_REVISE 또는 BLOCK. CRISIS → `SafetyFlag` 저장 후 BLOCK. BLOCK이면 `story.block()`.
- **ASK_REVISE와 BLOCK이면 입력을 저장하지 않고 AI 그래프로도 보내지 않는다.** 인터뷰 답변이 ASK_REVISE면 같은 질문을 다시 준다. 인터뷰 답변은 그래프에 보내기 전에 검사한다(스펙 v0.3). skip은 검사하지 않는다.
- `category`는 AI가 준 값(스펙 4장 목록: violence, harmful_discipline, discrimination, illegal, blame_child, self_harm, abuse_neglect, harm_intent, other)을 그대로 저장하고, 비어 있으면 `other`. 값 목록을 enum으로 묶지 않는다(AI 쪽에서 늘어날 수 있다).
- 차단 응답에는 `helpKind`(CRISIS → `CHILD_SAFETY`, REVISE로 차단 → `PARENTING`)만 준다. **상담 기관 이름과 번호는 백엔드에 넣지 않는다**(프론트가 helpKind로 보여준다).
- BLOCKED 동화의 모든 변경 요청은 `STORY_BLOCKED`. 나가기(DELETE)와 조회(draft)만 된다. 다시 만들기 메모로 REVIEWING에서도 차단될 수 있다.
- **결과 위기 신호**(AI 결과의 `safety`가 REVISE, CRISIS): `SafetyFlag`(source OUTPUT)를 감사용으로 남기고 버전을 만들지 않고 실행·스토리 FAILED(`OUTPUT_UNSAFE`). 부모는 다시 시도할 수 있다. **부모의 REVISE 횟수에는 넣지 않는다**(`countByStoryIdAndLevelAndSourceNot(OUTPUT)`). 다만 `occurrenceNo`는 UNIQUE(story, level, occurrence_no)라 OUTPUT까지 포함한 개수로 정한다.

## 인증 규칙

- 로그인은 이메일 + 비밀번호. 서버 세션(Spring Session JDBC, PostgreSQL)으로 유지한다. **JWT를 쓰지 않는다.**
- 세션 유휴 만료 30분, 세션 쿠키 `FSESSION`. 로그인 상태 유지(remember-me)는 persistent token 방식으로 90일, 쿠키 `FREMEMBER`, 기본값 꺼짐.
- 쿠키는 모두 HttpOnly(CSRF 쿠키 제외), SameSite=Lax, Secure는 `COOKIE_SECURE`.
- CSRF는 켠다(SPA 방식). 프론트는 앱을 열 때와 로그아웃한 뒤 `GET /api/auth/csrf`를 불러 `XSRF-TOKEN` 쿠키를 받고, 상태를 바꾸는 요청에 `X-XSRF-TOKEN` 헤더로 돌려보낸다. 로그인, 가입도 검사한다.
- 로그인은 필터가 아닌 API(`LoginService` + `LoginSuccessHandler`)에서 처리하고 `SecurityContextRepository`로 명시적으로 저장한다. 로그아웃은 `LogoutFilter`(`POST /api/auth/logout`).
- 인증번호와 인증 결과는 세션에 저장한다(코드는 SHA-256 해시만). 세션에 넣는 객체는 `Serializable` record로 만들고 `toString`에서 번호를 가린다.
- 비밀번호 변경과 탈퇴는 `SessionRevoker.revokeAll(email)`로 그 회원의 모든 세션과 remember-me 토큰을 지운다. Principal 이름은 이메일이다.
- 현재 시각은 `Clock` 빈으로 구한다. **`Instant.now()`를 직접 부르지 않는다.** JPA auditing(created_at)도 이 Clock을 따른다.
- 로그인 컨트롤러에서는 `@AuthenticationPrincipal UserPrincipal`로 `userId`를 꺼낸다.

### 환경 변수

도메인(`firstoryofficial`)과 출처는 코드에 적지 않고 환경 변수로만 받는다.

| 환경 변수 | 로컬 기본값 | 운영 | 용도 |
|---|---|---|---|
| `COOKIE_SECURE` | `false` | `true` | 쿠키 Secure 속성 |
| `COOKIE_DOMAIN` | (비움) | (비움) 또는 최상위 도메인 | API를 서브도메인으로 나눌 때만. 비우면 host-only 쿠키 |
| `ALLOWED_ORIGINS` | `http://localhost:5173` | (비움) 또는 프론트 출처 | CORS. 쉼표로 구분. 비우면 CORS 설정을 등록하지 않음 |
| `REMEMBER_ME_KEY` | 로컬용 임의값 | 비밀값 | remember-me 서명 키 |
| `PHONE_AES_KEY`, `PHONE_HMAC_KEY` | 로컬용 값 | 비밀값 | 휴대폰 번호 암호화, 해시 |
| `AI_MODE` | `http` (local, test, dev 프로필은 `fake`) | `http` | AI 클라이언트 구현 |
| `AI_BASE_URL` | `http://localhost:8000` | AI 서버 주소 | FastAPI 기본 주소 |
| `AI_INTERNAL_TOKEN` | (비움) | 비밀값 | `X-Internal-Token`. `http`면 필수 |
| `STORAGE_TYPE` | `local` | `s3` | 파일 저장소 구현 |
| `STORAGE_LOCAL_ROOT` | `./data/storage` | 쓰지 않음 | 로컬 파일 저장소 루트 (`local`일 때) |
| `STORAGE_S3_BUCKET`, `STORAGE_S3_REGION` | (비움), `ap-northeast-2` | 버킷 이름, 리전 | S3 저장소 (`s3`일 때. 버킷은 필수) |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | (비움) | 비밀값 (EC2는 IAM 역할이면 비움) | S3 접근 키. 그 버킷만 읽고 쓰는 IAM 사용자의 키 |
| `SERVER_PORT` (없으면 `PORT`) | `8080` | 플랫폼이 넣는 값 | 서버 포트. Railway는 `PORT`를 넣어 준다 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 로컬 docker compose | 비밀번호는 비밀값 | JDBC 주소, DB 계정. dev는 기본값 없음 |
| `FIRSTORY_AI_FAKE_STAGE_DURATION` | (local 40s) | 쓰지 않음 | 가짜 AI 생성 단계 시간. smoke 때 `5s`로 덮어쓴다 |

고정 인증번호 `firstory.sms.local-fixed-code`는 `LocalFixedVerificationCode`(`@Profile({"local", "dev"})`)만 읽는다. 그 밖의 프로필(prod 등, 프로필 없음 포함)에 이 값이 있으면 `LocalFixedVerificationCode.Guard`가 기동을 막는다. 번호 생성만 바꾸고 해시 저장·만료·시도 횟수 규칙은 그대로다.

## 개인정보 규칙 (반드시 지킬 것)

- 휴대폰 번호는 `PhoneCrypto`로 암호화해 `phone_enc`에, 중복 체크용 HMAC은 `phone_hash`에 저장한다. 평문 저장 금지.
- 아이 호칭(`Child.displayName`)은 AI 서비스로 보내지 않는다. `ChildSnapshot`에도 넣지 않는다.
- 휴대폰 번호, 아이 정보, 부모 입력 서술, 인터뷰 답변을 로그에 남기지 않는다. 비밀번호와 인증번호도 남기지 않는다(로컬 `LoggingSmsSender`의 인증번호만 예외).
- 휴대폰 번호는 응답에도 가린 형태(`PhoneNumber.masked()`, `010-****-5678`)만 내려준다.

## 에러와 문구

- 비즈니스 오류는 `BusinessException(ErrorCode)`로 던진다. `GlobalExceptionHandler`가 `{code, message}`로 응답한다.
- `VALIDATION_FAILED`는 `fields[{field, message}]`를 함께 준다. 본문을 읽지 못한 경우(깨진 JSON, 없는 enum 값, 모르는 Map 키, 타입 불일치)도 `RequestBodyErrors`가 Jackson 경로로 필드를 찾는다. 경로에는 DTO 필드 이름과 목록 순번만 쓰고 **Map 키와 받은 값, Jackson 예외 메시지는 응답에도 로그에도 넣지 않는다**(`UnreadableBodyIntegrationTest`).
- 사용자에게 보이는 메시지는 한국어 해요체. 무엇이 잘못됐고 어떻게 고치는지 쓴다. 사과하지 않는다.

## 테스트

- 도메인 규칙(상태 전이, 동의 검증, 관심사 정규화, 호칭 부여, 버전 전환)은 Spring 없이 단위 테스트.
- Repository 테스트는 jsonb와 부분 인덱스가 있어 H2를 쓰지 않는다. Testcontainers PostgreSQL을 쓴다.
- API 통합 테스트는 `com.firstory.support.IntegrationTest`를 상속한다(`@SpringBootTest` + MockMvc + Testcontainers, `test` 프로필). `Browser`가 기기 하나의 쿠키와 CSRF 헤더를 들고 다니고, `MutableClock`으로 시간을 옮기고, `RecordingSmsSender`에서 인증번호를 읽는다. AI는 `FakeAiClient` 빈을 주입받아 쓴다(장애 설정은 `@AfterEach`에서 `clearFailure()`).
- `HttpAiClient`는 `MockRestServiceServer`로 경로, 헤더, 요청 JSON 키 구조, 오류 변환을 확인한다.
- 확정된 동화가 필요한 테스트는 `StoryFlow.completed(browser, poller)`(가입한 브라우저로 만들기 → 생성 → 확정)를, 상태별 동화가 많이 필요한 서재 테스트는 `StoryStates`(DB에 바로 만든다)를 쓴다. 쿼리 수는 Hibernate `Statistics.getPrepareStatementCount()`로 센다(쪽이 가득 차야 개수 쿼리가 나가므로 가득 찬 쪽끼리 비교한다).
- `FullJourneyIntegrationTest`가 가입부터 함께 읽기, 로그아웃까지 실제 사용 순서를 한 번에 확인한다.
- 생성 통합 테스트는 `GenerationPoller.pollOnce()`를 직접 부른다(처음 만들기·다시 시도 5번, 글 다시 4번, 그림만 다시 3번이면 REVIEWING). 시간 초과는 `MutableClock`을 옮긴다. 문자 알림은 `RecordingSmsSender.lastText`로 본다.

## 아직 정해지지 않은 것

추측해서 구현하지 말고 `// TODO(PM): 설명` 주석과 함께 가장 단순한 형태로 남긴다.

- 화면 문구(label) 임시값: `InterestCode`, `SituationCategory`, `MomentReaction`, `Emotion`, `MessageDirection` (enum에 `TODO(PM)`)
- CRT-01 "알려주고 싶은 주제" 유형
- 확정 후 사용하지 않는 버전 보관 정책
- 차단된 동화의 입력 데이터를 보관할지 바로 지울지 (ST-05). 지금은 그대로 둔다 (`SafetyGuard`)
- 위기 신호 차단 화면의 상담 기관 이름과 번호 (G-FACT). 백엔드는 `helpKind`만 준다
- 전하고 싶은 말(CRT-04) 글자 수 제한. 지금은 제한하지 않는다 (`MessageRequest`)
- 알림 문구 (`NotificationTemplate` 임시값). 다시 만들기·다시 시도의 시작 알림을 보낼지 (지금은 보내지 않는다)
- 삭제한 동화의 입력 원문과 그림 파일 실제 파기 (PRD 36). 지금은 소프트 삭제만
- 탈퇴 시 개인정보 파기 시점 (PRD 36). 지금은 회원만 소프트 삭제하고 아이, 동화 데이터는 그대로 둔다
- 비밀번호 규칙 (임시: 8~64자, 영문과 숫자 각각 1개 이상, `PasswordPolicy`)
- 아이 대상 연령 범위 (임시: 0~144개월, `BirthYmPolicy.MAX_AGE_MONTHS`)
- 서비스 최상위 도메인(.com, .kr, .co.kr). 코드에는 적지 않는다
- 웹 푸시(PUSH_SUBSCRIPTIONS)는 2차 범위. 지금 만들지 않는다.
- 카카오 로그인은 하지 않는다. 이메일 + 비밀번호 + 문자 인증 휴대폰 번호로만 가입한다.

## 문서

- `docs/FIRSTORY-AI-API-SPEC-v0.3.md`: AI 서비스(FastAPI) API 스펙
- `docs/frontend/`: 프론트 담당자용 문서 (위 "프론트 전달 문서")
- `docs/deploy/railway.md`: 개발 서버(Railway) 배포와 환경 변수 목록
- TASK-01~08 작업 문서는 끝나서 지웠다. 규칙은 이 파일에 모두 옮겼고, 원문은 git 기록(태그 `v0.1.0-frontend`)에 있다. 코드 주석의 `TASK-0n`은 그 작업에서 만든 것이라는 표시다.
