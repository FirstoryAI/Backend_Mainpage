# FIRSTORY 백엔드 시작하기 (프론트 담당자용)

내 컴퓨터에서 백엔드를 띄우고 화면에 API를 붙이기 위한 문서다. AI 서버 없이 **가짜 AI**로 동화 만들기부터 함께 읽기까지 모두 돌아간다.

| 문서 | 내용 |
|---|---|
| [README.md](README.md) | 지금 이 문서. 준비, 실행, 프론트 개발 서버 연결, 자주 나는 문제 |
| [auth.md](auth.md) | 쿠키, CSRF, 401 처리, 민감 작업 |
| [screens.md](screens.md) | 화면별로 부를 API와 응답에서 볼 필드 |
| [errors.md](errors.md) | 오류 코드 전체 |
| [fake-ai.md](fake-ai.md) | 가짜 AI와 진짜 AI의 차이, 실패 흉내 표시 |
| [firstory.http](firstory.http) | 바로 실행해 보는 요청 모음 (IntelliJ / VS Code) |
| [status.md](status.md) | 지금 되는 것과 안 되는 것 |

## 1. 준비물

- **Docker Desktop** (PostgreSQL을 띄운다). 켜 두어야 한다.
- **프로젝트용 JDK 21은 설치하지 않아도 된다.** Gradle이 툴체인으로 받아서 쓴다(처음 한 번은 몇 분 걸린다). 다만 `./gradlew` 자체를 실행할 Java(17 이상, 버전 무관)는 PATH나 `JAVA_HOME`에 있어야 한다. `java -version`이 안 나오면 아무 JDK 17+를 설치한다.
- Git

## 2. 실행

저장소 루트에서 한 번만:

```bash
cp .env.example .env                                                        # DB 포트 55432
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
Copy-Item src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

띄울 때마다:

```bash
docker compose up -d                                              # PostgreSQL (55432)
./gradlew bootRun --args='--spring.profiles.active=local'         # 서버 (8081)
```

Windows PowerShell:

```powershell
docker compose up -d
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

로그에 `Started FirstoryApplication`이 나오면 준비됐다. 끌 때는 `Ctrl+C`, DB는 `docker compose stop`.

## 3. 주소

| 무엇 | 주소 |
|---|---|
| API | `http://localhost:8081/api/...` |
| Swagger UI | http://localhost:8081/swagger-ui/index.html |
| API 문서 JSON | http://localhost:8081/v3/api-docs |

전체 흐름을 한 번에 확인하려면 서버를 생성 단계 5초로 띄우고 smoke 스크립트를 돌린다(약 1~2분, 35단계).

```bash
FIRSTORY_AI_FAKE_STAGE_DURATION=5s ./gradlew bootRun --args='--spring.profiles.active=local'   # 창 1
bash scripts/smoke-local.sh                                                                     # 창 2 (Git Bash)
```

Swagger에서 직접 눌러 볼 때는 먼저 `GET /api/auth/csrf`를 실행한다. 이후 POST/PUT/PATCH/DELETE에는 Swagger가 CSRF 헤더를 알아서 붙인다.

## 4. 고정 인증번호 `000000`

local에서는 문자를 실제로 보내지 않고, 인증번호가 항상 **`000000`** 이다 (`application-local.yml`의 `firstory.sms.local-fixed-code`). 만료(3분), 5번 틀리면 다시 받기, 60초 재발송 제한 같은 규칙은 운영과 같다.

이 설정을 지우면 무작위 번호가 서버 로그에 `[SMS] 010-****-1234 ← [FIRSTORY] 인증번호 123456`처럼 찍힌다. 이 설정은 local 프로필에서만 동작하고, 다른 프로필에 넣으면 서버가 뜨지 않는다.

## 5. 프론트 개발 서버 연결

### 권장: 개발 서버 프록시

운영도 프론트와 API가 같은 주소(`/api`)에 붙는다. 프록시를 쓰면 개발 중에도 같은 출처라서 **CORS와 쿠키 문제가 없다.** 프론트 코드는 `fetch('/api/...')`처럼 상대 경로로 부른다.

```js
// vite.config.js
export default {
  server: {
    proxy: {
      '/api': 'http://localhost:8081',
      '/swagger-ui': 'http://localhost:8081',
      '/v3': 'http://localhost:8081',
    },
  },
}
```

### 프록시를 못 쓸 때: CORS

`application-local.yml`에 `allowed-origins: ${ALLOWED_ORIGINS:http://localhost:5173}`가 들어 있어 `http://localhost:5173`에서 `http://localhost:8081`로 바로 부를 수 있다. 다른 출처면 환경 변수 `ALLOWED_ORIGINS`(쉼표로 여러 개)로 바꾼다. 이때 주의할 점:

- **모든 요청에 쿠키를 포함한다.** `fetch(url, { credentials: 'include' })`, axios는 `withCredentials: true`. 빠뜨리면 로그인이 안 된 것처럼 401이 난다.
- **CSRF 헤더를 직접 붙인다.** `XSRF-TOKEN` 쿠키 값을 읽어 `X-XSRF-TOKEN` 헤더로 보낸다([auth.md](auth.md)). 쿠키는 포트를 구분하지 않아 `localhost:5173`의 JS가 `localhost:8081`이 내려준 `XSRF-TOKEN`을 읽을 수 있다.
- `localhost`와 `127.0.0.1`을 섞어 쓰지 않는다. 다른 사이트로 취급되어 쿠키가 따로 놀고 403/401이 난다.
- 허용한 요청 헤더는 `Content-Type`, `X-XSRF-TOKEN`뿐이다. 다른 헤더를 붙이면 CORS preflight에서 막힌다.

## 6. 자주 나는 문제

| 증상 | 원인과 해결 |
|---|---|
| `Port 8081 was already in use` | 이미 서버가 떠 있다. 이전 bootRun 창을 끈다. Windows: `netstat -ano \| findstr :8081`로 PID를 찾아 `taskkill /PID {PID} /F` |
| `Connection to localhost:55432 refused` | Docker Desktop이 꺼져 있거나 DB를 안 띄웠다. Docker Desktop을 켜고 `docker compose up -d` |
| DB 포트 충돌(`55432 ... address already in use`) | `.env`의 `POSTGRES_PORT`와 `application-local.yml`의 DB 주소 포트를 같은 다른 값으로 바꾼다 |
| `No qualifying bean of type 'SmsSender'`로 기동 실패 | **프로필 없이 실행했다.** `--spring.profiles.active=local`을 꼭 붙인다 |
| `AI_INTERNAL_TOKEN이 필요해요`로 기동 실패 | 마찬가지로 local 프로필이 빠졌다 (local은 가짜 AI) |
| `local-fixed-code는 local 프로필에서만` | local이 아닌 프로필 설정에 고정 인증번호가 들어갔다. 지운다 |
| 상태를 바꾸는 요청이 403 `FORBIDDEN` | CSRF 헤더가 없다. 앱을 열 때와 로그아웃 뒤 `GET /api/auth/csrf`를 불렀는지 확인 |
| 모든 요청이 401 | 쿠키가 안 붙는다. 프록시를 쓰거나 `credentials: 'include'` |
| 서버를 다시 띄웠더니 만들던 동화의 인터뷰·생성이 `AI_STATE_MISMATCH` | 가짜 AI는 진행 상태를 메모리에만 둔다. 새 동화를 만든다 ([fake-ai.md](fake-ai.md)) |
| Windows에서 curl로 한글 본문을 보내면 400 `VALIDATION_FAILED` | Git Bash·PowerShell이 인자의 한글을 시스템 코드 페이지로 바꾼다. 본문을 UTF-8 파일로 저장해 `--data-binary @body.json`으로 보낸다 (`scripts/smoke-local.sh` 참고) |
| 예전에 만든 동화의 그림이 404 | `data/storage` 폴더를 지웠다. 새로 만든다 |
