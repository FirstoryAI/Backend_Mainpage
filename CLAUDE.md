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
./gradlew bootRun
```

### 로컬 실행 (포트가 겹치는 개발 PC)

로컬에 PostgreSQL(5432)이나 다른 앱(8080)이 이미 떠 있으면 `local` 프로필로 띄운다. DB 55432, 서버 8081을 쓴다.

```bash
cp .env.example .env                                                        # POSTGRES_PORT=55432
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
docker compose up -d                                                        # .env를 읽어 55432로 띄움
./gradlew bootRun --args='--spring.profiles.active=local'
```

`.env`와 `application-local.yml`은 커밋하지 않는다. 설정을 바꾸면 `.example` 파일도 함께 고친다.

## 패키지 구조 (도메인별)

```
com.firstory
├── FirstoryApplication   하위 패키지를 모두 스캔하도록 루트에 둔다
├── global                config, entity(BaseTimeEntity), error, crypto, converter
└── domain
    ├── user              회원, 동의
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
- 다시 만들기(`Story.requestRegeneration`)는 `REVIEWING`에서만 할 수 있고 `regenerateCount`를 1 올린다. 실패 후 다시 시도(`Story.retry`, `FAILED → REQUESTED`)는 다시 만들기 횟수에 포함하지 않는다. 한도를 다 써도 재시도는 할 수 있다.
- `PhoneNumber.toString()`은 번호를 가린다(`PhoneNumber[***]`). 로그에 실수로 찍혀도 번호가 남지 않게 하기 위함이다. 번호가 필요하면 `digits()`나 `display()`를 명시적으로 쓰고, 그 값은 로그에 남기지 않는다.
- 위기 신호는 동화 단위로 센다. 1회째 경고 팝업, 2회째 `BLOCKED`.
- 아이 프로필은 "다음에도 이 아이 정보로 만들기"를 체크했을 때만 저장한다. 동화는 항상 `StoryInput.childSnapshot`을 기준으로 만든다.

## 개인정보 규칙 (반드시 지킬 것)

- 휴대폰 번호는 `PhoneCrypto`로 암호화해 `phone_enc`에, 중복 체크용 HMAC은 `phone_hash`에 저장한다. 평문 저장 금지.
- 아이 호칭(`Child.displayName`)은 AI 서비스로 보내지 않는다. `ChildSnapshot`에도 넣지 않는다.
- 휴대폰 번호, 아이 정보, 부모 입력 서술, 인터뷰 답변을 로그에 남기지 않는다.

## 에러와 문구

- 비즈니스 오류는 `BusinessException(ErrorCode)`로 던진다. `GlobalExceptionHandler`가 `{code, message}`로 응답한다.
- 사용자에게 보이는 메시지는 한국어 해요체. 무엇이 잘못됐고 어떻게 고치는지 쓴다. 사과하지 않는다.

## 테스트

- 도메인 규칙(상태 전이, 동의 검증, 관심사 정규화, 호칭 부여, 버전 전환)은 Spring 없이 단위 테스트.
- Repository 테스트는 jsonb와 부분 인덱스가 있어 H2를 쓰지 않는다. Testcontainers PostgreSQL을 쓴다.

## 아직 정해지지 않은 것

추측해서 구현하지 말고 `// TODO(PM): 설명` 주석과 함께 가장 단순한 형태로 남긴다.

- 다시 만들기 최대 횟수 (임시값 3, `firstory.policy.max-regenerations`)
- 상황 분류(situation_category), 감정(emotion) 값 목록 → 문자열 코드로 저장
- CRT-01 "알려주고 싶은 주제" 유형
- 확정 후 사용하지 않는 버전 보관 정책
- 탈퇴 시 개인정보 파기 시점 (PRD 36)
- 웹 푸시(PUSH_SUBSCRIPTIONS)는 2차 범위. 지금 만들지 않는다.
- 카카오 로그인은 하지 않는다. 이메일 + 비밀번호 + 문자 인증 휴대폰 번호로만 가입한다.

## 작업 문서

- `docs/tasks/TASK-01-setup-and-entities.md`: 프로젝트 세팅과 JPA 엔티티
