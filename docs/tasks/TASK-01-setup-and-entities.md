# TASK-01. 프로젝트 세팅과 JPA 엔티티

`CLAUDE.md`의 규칙을 먼저 읽고 시작한다. 이 작업이 끝나면 앱이 로컬 PostgreSQL에 붙어 Flyway로 스키마를 만들고, `ddl-auto=validate`를 통과한 상태로 떠야 한다.

## 범위

**포함**: Gradle 세팅, 설정 파일, `global` 패키지, 16개 테이블의 엔티티와 enum, 정책 클래스, Repository, 단위 테스트와 Repository 테스트

**제외** (다음 작업): Controller, Service, Spring Security, 문자 발송 연동, AI 서비스 클라이언트, S3 업로드

## 작업 순서

각 단계가 끝날 때마다 `./gradlew build`가 통과해야 다음으로 넘어간다. 단계마다 커밋한다.

1. 프로젝트 세팅
2. `global` 패키지
3. 회원 (`user`)
4. 아이 (`child`)
5. 동화 (`story`)
6. 생성 (`generation`)
7. 대화 가이드 (`guide`), 함께 읽기 (`reading`)
8. 위기 신호 (`safety`), 알림 (`notification`)
9. 테스트, 앱 기동 확인

---

## 1. 프로젝트 세팅

### build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.1'
    id 'io.spring.dependency-management' version '1.1.7'   // 최신 패치로
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.security:spring-security-crypto'   // BCrypt만

    // Flyway: 스타터 + PostgreSQL 모듈. 버전 적지 않음
    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    implementation 'org.flywaydb:flyway-database-postgresql'
    runtimeOnly 'org.postgresql:postgresql'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'   // @DataJpaTest
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:testcontainers-postgresql'
    testImplementation 'org.testcontainers:testcontainers-junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

**세팅 직후 반드시 확인할 것**

1. `./gradlew dependencyInsight --dependency flyway --configuration runtimeClasspath`
   → `flyway-core`와 `flyway-database-postgresql`이 **같은 버전**이어야 한다. (Spring Boot 4.1.0 기준 Flyway 12.4.0)
2. 아티팩트 이름이 해석되지 않으면(`Could not find ...`) 추측해서 바꾸지 말고, Spring Boot 4.1.1 BOM(`spring-boot-dependencies`)에서 실제 이름을 확인한 뒤 고친다.
   - Spring Boot 4에서 이름이 바뀐 것: 웹 스타터(`starter-web` → `starter-webmvc`), JPA 테스트 스타터 분리, Testcontainers 2.x 모듈 이름(`postgresql` → `testcontainers-postgresql`)

### Spring Boot 4 주의사항

| 증상 | 원인 | 해결 |
|---|---|---|
| 앱이 뜰 때 `Schema-validation: missing table [users]` | Flyway 자동 설정이 안 돌아서 마이그레이션이 실행되지 않음 | `spring-boot-starter-flyway`가 있는지 확인. `flyway-core`만으로는 안 됨 |
| `Unsupported Database: PostgreSQL 16` | `flyway-database-postgresql` 누락 또는 `flyway-core`와 버전 불일치 | 두 모듈 모두 버전 없이 선언, dependencyInsight로 확인 |
| `@DataJpaTest`를 찾을 수 없음 | 테스트 자동 설정도 모듈로 분리됨 | `spring-boot-starter-data-jpa-test` 추가 |
| jsonb 컬럼 저장·조회 시 FormatMapper 관련 오류 | Spring Boot 4는 Jackson 3이 기본이라 Hibernate의 JSON 매핑이 Jackson을 못 찾을 수 있음 | Repository 테스트의 jsonb 왕복 테스트로 확인. 실패하면 사용자에게 알리고, Hibernate가 지원하는 Jackson 연동 설정을 찾아 적용 |
| Hibernate 7 경고나 import 오류 | Hibernate 7에서 바뀐 API | `@SQLRestriction`, `@JdbcTypeCode(SqlTypes.JSON)`는 그대로 사용 가능. 다른 deprecated API는 쓰지 않는다 |

### docker-compose.yml

`postgres:16` (DB/계정/비밀번호 모두 `firstory`, 5432), `redis:7` (6379).

### application.yml

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/firstory}
    username: ${DB_USERNAME:firstory}
    password: ${DB_PASSWORD:firstory}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_batch_fetch_size: 100
        jdbc.time_zone: UTC
  flyway:
    enabled: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

firstory:
  crypto:
    phone-aes-key: ${PHONE_AES_KEY:...}     # 32바이트 Base64. 로컬용 기본값만 넣고 운영은 환경 변수
    phone-hmac-key: ${PHONE_HMAC_KEY:...}
  policy:
    max-regenerations: ${MAX_REGENERATIONS:3}   # TODO(PM): 결정 4번
  ai:
    base-url: ${AI_BASE_URL:http://localhost:8000}
```

### Flyway

`src/main/resources/db/migration/V1__init.sql`은 **이미 제공되어 있다. 내용을 바꾸지 않는다.** 엔티티를 이 SQL에 맞춘다.

---

## 2. global 패키지

| 클래스 | 내용 |
|---|---|
| `FirstoryApplication` | `@SpringBootApplication` + `@ConfigurationPropertiesScan` |
| `config.JpaConfig` | `@EnableJpaAuditing` |
| `config.FirstoryProperties` | `@ConfigurationProperties("firstory")` record. 하위 record `Crypto(phoneAesKey, phoneHmacKey)`, `Policy(maxRegenerations)`, `Ai(baseUrl)` |
| `entity.BaseTimeEntity` | `@MappedSuperclass`, `@CreatedDate createdAt`, `@LastModifiedDate updatedAt` (둘 다 `Instant`, not null, createdAt은 updatable=false) |
| `error.ErrorCode` | enum (HttpStatus, 한국어 메시지). 아래 목록 |
| `error.BusinessException` | `RuntimeException` + `ErrorCode` |
| `error.GlobalExceptionHandler` | `@RestControllerAdvice`. `BusinessException` → `{code, message}` |
| `crypto.PhoneNumber` | 휴대폰 번호 값 객체 (아래) |
| `crypto.PhoneCrypto` | 암호화와 해시 (아래) |
| `converter.YearMonthStringConverter` | `YearMonth` ↔ `"2021-03"` |

### ErrorCode 목록

| 코드 | HTTP | 메시지 |
|---|---|---|
| REQUIRED_CONSENT_MISSING | 400 | 필수 항목에 모두 동의해야 가입할 수 있어요. |
| INVALID_PHONE_FORMAT | 400 | 010-0000-0000 형식으로 입력해 주세요. |
| DUPLICATE_EMAIL | 409 | 이미 가입된 이메일이에요. |
| DUPLICATE_PHONE | 409 | 이미 가입된 휴대폰 번호예요. |
| INTERESTS_LIMIT_EXCEEDED | 400 | 좋아하는 것은 5개까지 입력할 수 있어요. |
| INTEREST_TOO_LONG | 400 | 좋아하는 것은 한 개당 20자까지 입력할 수 있어요. |
| CHILD_LABEL_EXHAUSTED | 400 | 저장할 수 있는 아이 수를 넘었어요. |
| CHILD_NOT_FOUND | 404 | 아이 정보를 찾을 수 없어요. |
| STORY_NOT_FOUND | 404 | 동화를 찾을 수 없어요. |
| INVALID_STATUS_TRANSITION | 409 | 지금 상태에서는 할 수 없는 작업이에요. |
| EMOTION_ONLY_FOR_PAST | 400 | 감정은 있었던 일에서만 입력할 수 있어요. |
| VERSION_NOT_IN_STORY | 400 | 이 동화의 버전이 아니에요. |
| REGENERATION_LIMIT_REACHED | 409 | 다시 만들기 횟수를 모두 썼어요. |
| PAGE_OUT_OF_RANGE | 400 | 없는 쪽이에요. |

### PhoneNumber

- record `PhoneNumber(String digits)`. compact constructor에서 `^010\d{8}$` 검증, 실패 시 `INVALID_PHONE_FORMAT`.
- `static fromDisplay(String)`: `^010-\d{4}-\d{4}$`만 허용(앞뒤 공백 trim), 하이픈 제거해 생성.
- `display()`: `010-1234-5678` 형식으로 반환.

### PhoneCrypto (`@Component`)

- `encrypt(PhoneNumber) → String`: AES-256-GCM, 매번 랜덤 12바이트 IV. 저장값은 `Base64(IV + 암호문)`.
- `decrypt(String) → PhoneNumber`.
- `hash(PhoneNumber) → String`: HMAC-SHA256 hex(64자). 같은 번호는 항상 같은 값 → `users.phone_hash` 중복 체크용.
- 생성자에서 AES 키가 32바이트가 아니면 `IllegalStateException`.

---

## 3. 회원 (user)

### User → `users`

| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long | IDENTITY |
| email | String | 소문자 + trim해서 저장. UNIQUE는 DB 부분 인덱스가 담당(엔티티에 `unique=true` 쓰지 않음) |
| passwordHash | String | BCrypt |
| phoneEnc | String | |
| phoneHash | String | UNIQUE는 DB 부분 인덱스 |
| phoneVerifiedAt | Instant | not null. 문자 인증을 마쳐야 가입 가능 |
| onboardingSeenAt | Instant | ONB-01 첫 방문 팝업 1회 |
| deletedAt | Instant | 소프트 삭제 |

- `@Table(name = "users")`, `@SQLRestriction("deleted_at IS NULL")`
- 메서드: `static signUp(email, passwordHash, phoneEnc, phoneHash, phoneVerifiedAt)`, `markOnboardingSeen(now)`(이미 있으면 유지), `hasSeenOnboarding()`, `changePhone(enc, hash, verifiedAt)`, `withdraw(now)`

### ConsentType (enum)

| 값 | required | label |
|---|---|---|
| GUARDIAN | true | 아이의 보호자(법정대리인)예요 |
| TERMS | true | 서비스 이용약관 |
| PRIVACY | true | 아이 정보를 포함한 개인정보 수집·이용 |
| SMS_NOTIFY | false | 동화 제작 소식을 문자로 받기 |

### ConsentPolicy (정적 유틸)

- `validateRequired(Map<ConsentType, Boolean>)`: required 항목 중 true가 아닌 게 있으면 `REQUIRED_CONSENT_MISSING`. 선택 항목은 검사하지 않는다.

### UserConsent → `user_consents`

- 필드: user(ManyToOne), consentType, agreed, termsVersion, agreedAt(agreed일 때만), withdrawnAt
- 메서드: `static record(user, type, agreed, termsVersion, now)`, `withdraw(now)`(required 항목이면 `IllegalStateException`), `isActive()`

### Repository

- `UserRepository`: `findByEmail`, `existsByEmail`, `existsByPhoneHash`
- `UserConsentRepository`: `findAllByUserId`, `findTopByUserIdAndConsentTypeOrderByIdDesc`

---

## 4. 아이 (child)

### enum

- `Gender`: FEMALE(여자아이), MALE(남자아이), UNDISCLOSED(밝히지 않을래요)
- `Trait`: SENSITIVE(마음이 여려요), SLOW_TO_WARM(낯선 곳에 시간이 걸려요), SHY(수줍음이 많아요), ACTIVE(활발해요), CURIOUS(호기심이 많아요), STUBBORN(고집이 있어요)
  - 각 enum에 화면 문구 `label` 필드를 둔다.

### Interests (정적 유틸)

`normalize(List<String>) → List<String>`:
- null이면 빈 리스트
- 각 값 strip, 빈 값 제거, 순서 유지하며 중복 제거
- 한 개가 20자 초과면 `INTEREST_TOO_LONG`
- 결과가 5개 초과면 `INTERESTS_LIMIT_EXCEEDED`

### ChildLabelPolicy (정적 유틸)

`nextLabel(Collection<String> labelsInUse)`: "아이 A"부터 "아이 Z"까지 순서대로 보고 **사용 중이 아닌 가장 앞 글자**를 반환. 모두 쓰였으면 `CHILD_LABEL_EXHAUSTED`.
(예: A, B가 있고 A가 삭제되면 다음은 다시 "아이 A")

### Child → `children`

| 필드 | 매핑 |
|---|---|
| user | `@ManyToOne(LAZY, optional=false)` |
| displayName | 기본 "아이 A". 부모가 수정 가능. **AI로 보내지 않음** |
| birthYm | `YearMonth`, `@Convert(YearMonthStringConverter)`, varchar(7) |
| gender | enum STRING |
| traits | `@ElementCollection(fetch=LAZY)` + `@CollectionTable(name="child_traits", joinColumns=@JoinColumn(name="child_id"))` + `@Enumerated(STRING) @Column(name="trait_code")`, `Set<Trait>` |
| interests | `List<String>`, jsonb |
| deletedAt | 소프트 삭제, `@SQLRestriction` |

- 메서드: `static save(user, displayName, birthYm, gender, traits, interests)`, `updateProfile(birthYm, gender, traits, interests)`, `rename(displayName)`(strip 후 1~30자), `delete(now)`, `isOwnedBy(userId)`, `ageMonthsAt(LocalDate)`
- `save`, `updateProfile`은 내부에서 `Interests.normalize`를 거친다.

### ChildRepository

- `findAllByUserIdOrderByIdAsc(userId)`
- `findByIdAndUserId(id, userId)`
- `@Query("select c.displayName from Child c where c.user.id = :userId") List<String> findDisplayNamesByUserId(userId)`

---

## 5. 동화 (story)

### StoryStatus (enum, 전이 규칙 포함)

| 현재 | 갈 수 있는 상태 |
|---|---|
| DRAFT | INTERVIEWING, BLOCKED |
| INTERVIEWING | CONFIRMING, BLOCKED |
| CONFIRMING | REQUESTED, BLOCKED |
| REQUESTED | GENERATING_TEXT, GENERATING_IMAGES(그림만 다시), FAILED |
| GENERATING_TEXT | GENERATING_IMAGES, FAILED |
| GENERATING_IMAGES | REVIEWING, FAILED |
| REVIEWING | REQUESTED(다시 만들기), COMPLETED(확정) |
| FAILED | REQUESTED(다시 시도) |
| COMPLETED | 없음 |
| BLOCKED | 없음 |

- `canTransitionTo(next)`, `isCreating()`(DRAFT/INTERVIEWING/CONFIRMING), `isGenerating()`(REQUESTED/GENERATING_*)
- `EnumMap<StoryStatus, Set<StoryStatus>>`를 static 블록에서 채운다.

### Story → `stories`

| 필드 | 매핑 |
|---|---|
| user | ManyToOne LAZY, not null |
| child | ManyToOne LAZY, nullable (저장 안 함이면 null) |
| threadId | String(64), unique |
| status | StoryStatus |
| currentVersion | `@ManyToOne(LAZY) @JoinColumn(name="current_version_id")` → StoryVersion |
| title | 현재 버전 제목 복사본 (서재 목록용) |
| regenerateCount | int |
| confirmedAt, firstReadCompletedAt, deletedAt | Instant |

메서드:
- `static start(user, child, threadId)`: status = DRAFT
- `changeStatus(next)`: 허용되지 않은 전이면 `INVALID_STATUS_TRANSITION`
- `showVersion(StoryVersion)`: 다른 동화의 버전이면 `VERSION_NOT_IN_STORY`, status가 REVIEWING이 아니면 `INVALID_STATUS_TRANSITION`. currentVersion과 title을 바꾼다. **버전 앞뒤 전환 모두 이 메서드**
- `requestRegeneration(int maxRegenerations)`: 횟수 초과면 `REGENERATION_LIMIT_REACHED`, 아니면 REQUESTED로 전이 후 카운트 +1
- `confirm(now)`: COMPLETED + confirmedAt
- `block()`: BLOCKED
- `markFirstReadCompleted(now)`: 처음 한 번만 기록
- `detachChild()`, `delete(now)`, `isOwnedBy(userId)`
- `libraryLabel()`:

| 상태 | 라벨 |
|---|---|
| DRAFT, INTERVIEWING, CONFIRMING | 만들던 이야기 |
| REQUESTED, GENERATING_TEXT, GENERATING_IMAGES | 만드는 중 |
| REVIEWING | 확인을 기다려요 |
| COMPLETED (firstReadCompletedAt 없음) | 아직 안 읽었어요 |
| COMPLETED (있음) | 함께 읽었어요 |
| FAILED | 만들지 못했어요 |
| BLOCKED | 만들 수 없는 이야기 |

### enum

- `SituationTiming`: PAST(있었던 일), UPCOMING(앞으로 겪을 일)  // TODO(PM): "알려주고 싶은 주제"
- `Tone`: PLAIN, WARM, UNDECIDED (ST-04)
- `InterviewEndReason`: SUFFICIENT, MAX_TURNS, USER_SKIP

### ChildSnapshot (record, jsonb로 저장)

`ChildSnapshot(int ageMonths, Gender gender, List<Trait> traits, List<String> interests)`
- `static of(Child, LocalDate today)`: 저장된 프로필에서
- `static of(YearMonth birthYm, Gender, Collection<Trait>, List<String> interests, LocalDate today)`: 저장 안 함인 경우. interests는 `Interests.normalize`
- **displayName 필드를 절대 넣지 않는다.**

### StoryInput → `story_inputs` (stories와 PK 공유 1:1)

- `@Id @Column(name="story_id") Long storyId` + `@MapsId @OneToOne(LAZY, optional=false) @JoinColumn(name="story_id") Story story`
- 필드: situationTiming, situationCategory(String), description(text), momentReaction(text), emotion(String), parentMessage(text), messageDirection(String), tone, childSnapshot(jsonb), interviewSummary(text), interviewEndReason
- 메서드:
  - `static begin(story)`
  - `writeSituation(timing, category, description, momentReaction, emotion)`: timing이 PAST가 아닌데 emotion이 있으면 `EMOTION_ONLY_FOR_PAST`
  - `writeChild(ChildSnapshot)`, `writeMessage(parentMessage, direction)`, `chooseTone(tone)`, `finishInterview(summary, reason)`

### InterviewTurn → `interview_turns`

- 필드: story, turnNo(1~5), question(text), answer(text, null이면 미답변), contextUpdate(`Map<String,Object>`, jsonb), answeredAt
- `@Table(uniqueConstraints = (story_id, turn_no))`, 상수 `MAX_TURNS = 5`
- 메서드: `static ask(story, turnNo, question)`(범위 밖이면 `IllegalArgumentException`), `answer(text, now)`, `updateContext(map)`, `isAnswered()`

### Repository

- `StoryRepository`: `findByIdAndUserId`, `findByThreadId`, `findAllByUserIdOrderByCreatedAtDesc`, `findAllByUserIdAndStatusInOrderByCreatedAtDesc`,
  `@Modifying(clearAutomatically = true) @Query("update Story s set s.child = null where s.child.id = :childId") int detachChild(childId)`
  (아이 프로필 삭제 시 서비스에서 먼저 호출. `@SQLRestriction` 때문에 삭제된 Child를 지연 로딩하면 예외가 나므로 연결을 끊어 둔다)
- `StoryInputRepository`
- `InterviewTurnRepository`: `findAllByStoryIdOrderByTurnNoAsc`, `findTopByStoryIdOrderByTurnNoDesc`

---

## 6. 생성 (generation)

### enum

- `RunTrigger`: INITIAL, REGENERATE, RETRY  (컬럼명 `trigger_type`. `trigger`는 SQL 예약어라 피함)
- `RegenerateScope`: ALL, CHARACTER_STORY, TEXT, IMAGES
  - AI 파이프라인 매핑: CHARACTER_STORY → design_story, TEXT → write_story(가이드와 그림도 다시 실행됨), IMAGES → illustrate_only
- `RunStatus`: RUNNING, SUCCEEDED, FAILED
- `ImageStatus`: PENDING, DONE, FAILED

### GenerationRun → `generation_runs`

- 필드: story, runNo, triggerType, scope, baseVersion(ManyToOne StoryVersion, nullable), feedback(text), status, currentNode, langsmithTraceId, inputTokens, outputTokens, imageCount, errorCode, startedAt, finishedAt
- 메서드:
  - `static start(story, runNo, trigger, scope, baseVersion, feedback, langsmithTraceId, now)`: status RUNNING
  - `progress(String node)`: currentNode 갱신 (GEN-01 4구간 표시)
  - `succeed(inputTokens, outputTokens, imageCount, now)`
  - `fail(errorCode, now)`

### StoryVersion → `story_versions`

- 필드: story, run(`@ManyToOne(LAZY) @JoinColumn(name="run_id", unique=true)`), versionNo, parentVersion(자기 참조 ManyToOne, nullable), title, checkpointId, design(`Map<String,Object>` jsonb), characterSheetKey
- 메서드: `static create(story, run, versionNo, parentVersion, title, checkpointId, design, characterSheetKey)`, `belongsTo(Story)`
- 버전은 나무 구조다. v1 → v2를 만든 뒤 v1로 돌아가 v3를 만들면 v3.parentVersion = v1. v2는 그대로 남는다.

### StoryPage → `story_pages`

- 필드: version, pageNo(1~10), text(text), imagePrompt(text), imageKey, imageStatus
- 메서드:
  - `static create(version, pageNo, text, imagePrompt)`: imageStatus PENDING
  - `imageDone(key)`, `imageFailed()`
  - `copyTo(StoryVersion target)`: 글과 이미지 키를 그대로 복사한 새 페이지. "그림만 다시" 시 바뀌지 않은 쪽은 S3 파일을 복사하지 않고 키만 공유한다.

### Repository

- `GenerationRunRepository`: `findTopByStoryIdOrderByRunNoDesc`, `findAllByStoryIdOrderByRunNoAsc`
- `StoryVersionRepository`: `findAllByStoryIdOrderByVersionNoAsc`, `findTopByStoryIdOrderByVersionNoDesc`, `findByIdAndStoryId`
- `StoryPageRepository`: `findAllByVersionIdOrderByPageNoAsc`

### 주의: stories ↔ story_versions 순환 참조

- `stories.current_version_id → story_versions`, `story_versions.story_id → stories`.
- 저장 순서: Story 저장(currentVersion null) → GenerationRun 저장 → StoryVersion 저장 → `story.showVersion(version)`.
- cascade는 어디에도 쓰지 않는다.

---

## 7. 대화 가이드 (guide), 함께 읽기 (reading)

### ReadingGuide → `reading_guides`

- 필드: version(ManyToOne, `unique=true`), beforeIntent(text), beforeTips(`List<String>` jsonb), bridgeQuestion(text), ifUndecided(text)
- `static create(...)`

### GuidePrompt → `guide_prompts`

- 필드: guide, seq, afterPage(1~10), question(text), purpose, parentHint(text)
- 주의: AI 결과 `04-guide.json`에서는 질문 본문 필드명이 `text`. DB와 엔티티는 `question`으로 두고 매핑은 이후 DTO에서 한다.

### ReadingSession → `reading_sessions`

- 필드: story, version, lastPage, startedAt, completedAt
- 메서드: `static start(story, version, now)`(lastPage 0), `moveTo(page)`(1~10 밖이면 `PAGE_OUT_OF_RANGE`), `complete(now)`, `isCompleted()`
- 완료 시 `Story.markFirstReadCompleted`를 부르는 건 서비스의 책임 (이번 작업 범위 밖)

### DialogueActionType (enum)

TALKED([이야기 나눴어요]), SKIPPED([건너뛰기])

### DialogueAction → `dialogue_actions`

- 필드: session, prompt, action. (session_id, prompt_id) UNIQUE
- `static record(session, prompt, action)`

### Repository

- `ReadingGuideRepository`: `findByVersionId`
- `GuidePromptRepository`: `findAllByGuideIdOrderBySeqAsc`
- `ReadingSessionRepository`: `findTopByStoryIdAndCompletedAtIsNullOrderByStartedAtDesc`(이어 읽기), `countByStoryIdAndCompletedAtIsNotNull`
- `DialogueActionRepository`: `countByAction(DialogueActionType)` (North Star 집계용)

---

## 8. 위기 신호 (safety), 알림 (notification)

### SafetySource, SafetyUserResponse, SafetyAction (enum)

- `SafetySource`: FORM, INTERVIEW
- `SafetyUserResponse`: HELP_OPENED("상담이 필요해요" 선택), DISMISSED(무시하고 계속)
- `SafetyAction`: WARN_POPUP, BLOCK

### SafetyPolicy (정적 유틸)

- `decide(int occurrenceNo) → SafetyAction`: 1이면 WARN_POPUP, 2 이상이면 BLOCK
- 횟수는 **동화 단위**. 새 동화는 0부터.
- 판단 자체는 AI 서비스가 맥락을 보고 한다. 죽음·이별 소재 자체는 허용되고, AI가 위험하거나 지나치게 부정적이라고 판단한 경우만 기록한다.

### SafetyFlag → `safety_flags`

- 필드: story, source, interviewTurn(nullable), category(String, AI 분류), aiReason(text), occurrenceNo, userResponse(nullable), detectedAt
- (story_id, occurrence_no) UNIQUE
- 메서드: `static record(story, source, interviewTurn, category, aiReason, occurrenceNo, now)`, `respond(SafetyUserResponse)`, `action()` → `SafetyPolicy.decide(occurrenceNo)`
- `SafetyFlagRepository`: `countByStoryId`

### enum

- `NotificationType`: STORY_STARTED, STORY_COMPLETED, STORY_FAILED  // 문구는 PM 버전으로 확정 예정
- `NotificationChannel`: SMS  // WEB_PUSH는 2차
- `NotificationStatus`: PENDING, SENT, FAILED

### Notification → `notifications`

- 필드: user, story, generationRun, type, channel, templateCode, status, providerMsgId, failReason, sentAt
- (generation_run_id, type) UNIQUE: 같은 실행에 같은 알림이 두 번 나가지 않게 한다. 다시 만들기나 재시도는 새 실행이라 다시 보낼 수 있다.
- 메서드: `static pending(user, story, run, type, channel, templateCode)`, `sent(providerMsgId, now)`, `failed(reason)`
- `NotificationRepository`: `existsByGenerationRunIdAndType`
- SMS_NOTIFY 동의 여부 확인은 서비스의 책임 (이번 작업 범위 밖)

---

## 9. 테스트

### 단위 테스트 (Spring 없이)

| 대상 | 확인할 것 |
|---|---|
| `StoryStatus` | 표의 허용 전이는 true, 그 외(예: COMPLETED → REQUESTED, DRAFT → REVIEWING)는 false |
| `Story` | 허용 안 된 전이 시 예외 / `showVersion`이 다른 동화 버전이면 예외 / REVIEWING이 아니면 예외 / v1 → v2 → v1 → v2 전환 가능 / `requestRegeneration` 한도 초과 시 예외, 버전 전환은 횟수 안 늘림 / `libraryLabel` 전 상태 |
| `ConsentPolicy` | 필수 3개 true면 통과 / 하나라도 false·누락이면 예외 / SMS_NOTIFY false여도 통과 |
| `Interests` | 5개 통과, 6개 예외 / 21자 예외 / 공백·중복 정리 / null → 빈 리스트 |
| `ChildLabelPolicy` | 빈 목록 → 아이 A / [A, B] → C / [B] → A / A~Z 모두 사용 → 예외 |
| `PhoneNumber` | `010-1234-5678` 통과, `01012345678`·`011-...`·`010-123-4567` 예외, `display()` 왕복 |
| `PhoneCrypto` | encrypt → decrypt 왕복 / 같은 번호 두 번 encrypt 결과가 다름 / hash는 같음 |
| `ChildSnapshot` | displayName이 포함되지 않음 (record 컴포넌트에 없음을 확인) |
| `SafetyPolicy` | 1 → WARN_POPUP, 2 → BLOCK |
| `StoryInput` | UPCOMING + emotion → 예외 |

### Repository 테스트 (`@DataJpaTest` + Testcontainers PostgreSQL 16)

- `@AutoConfigureTestDatabase(replace = NONE)`, `@ServiceConnection` 사용. Flyway가 V1을 적용한 DB에서 돈다.
- 확인할 것:
  - Child 저장 후 조회 시 traits(ElementCollection)와 interests(jsonb)가 그대로 돌아온다
  - StoryInput의 childSnapshot(jsonb record)이 왕복된다
  - 같은 email로 두 번째 User 저장 시 예외, 첫 번째를 `withdraw` 후에는 저장 가능
  - Story → Run → Version 저장 후 `showVersion`, 다시 조회하면 currentVersion이 맞다
  - `detachChild` 후 Story.child가 null
  - 삭제된 Child는 `findAllByUserIdOrderByIdAsc`에 안 나온다

### 앱 기동 확인

```bash
docker compose up -d
./gradlew bootRun
```

로그에 Flyway `V1__init` 적용과 Hibernate validate 통과가 보이고, 에러 없이 기동되면 완료.

---

## 완료 조건

- [ ] `./gradlew build` 통과 (모든 테스트 포함)
- [ ] `dependencyInsight`로 flyway 모듈 버전이 모두 같음을 확인했다
- [ ] 로컬 PostgreSQL로 `bootRun` 기동 성공 (`ddl-auto=validate` 통과)
- [ ] 엔티티에 `@OneToMany`, setter, `CascadeType`이 없다
- [ ] `ChildSnapshot`에 displayName이 없다
- [ ] 미정 사항은 `TODO(PM)` 주석으로 남겼다
- [ ] V1__init.sql을 수정하지 않았다
