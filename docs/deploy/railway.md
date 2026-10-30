# Railway 개발 서버 배포

프론트가 진짜에 가까운 환경에서 붙여 보는 **개발 서버(dev 프로필)** 배포 방법이다. 운영(prod)이 아니다.

dev 프로필(`application-dev.yml`)이 하는 일:

- Swagger 켬 (`/swagger-ui/index.html`)
- 가짜 AI(`AI_MODE=fake`), 속도는 local 예시값과 같다 (인터뷰 2초, 글 검사 1초, 생성 단계당 40초, 질문 1~5개)
- 고정 인증번호 `000000`, 문자는 보내지 않고 로그만 (`LoggingSmsSender`)
- 쿠키 Secure (`COOKIE_SECURE` 기본 `true`)

누구나 아무 번호로 가입할 수 있으니 **실제 개인정보를 넣지 않는다.**

## 준비

1. Railway 프로젝트에 **PostgreSQL** 서비스를 추가한다 (이름은 `Postgres`로 둔다. 아래 예시값이 이 이름을 참조한다).
2. 이 저장소로 서비스를 하나 더 만든다. 루트의 `Dockerfile`로 빌드된다.
3. 아래 환경 변수를 넣는다.
4. Settings → Deploy → **Healthcheck Path**에 `/api/health`를 넣는다 (로그인 불필요, `200 {"status":"ok"}`).
5. Settings → Networking → **Generate Domain**으로 공개 주소를 만든다. Railway가 HTTPS로 서비스한다.

## 환경 변수

`${{Postgres.PGHOST}}`처럼 쓴 값은 Railway의 참조 변수다. 그대로 넣으면 Railway가 PostgreSQL 서비스의 값으로 바꿔 준다.

| 환경 변수 | 필수 | 비밀값 | 설명 | dev 예시값 |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 필수 | 아니오 | 프로필. 없으면 `SmsSender` 빈이 없어 기동 실패 | `dev` |
| `DB_URL` | 필수 | 아니오 | JDBC 주소. Railway의 `DATABASE_URL`(`postgresql://...`)은 JDBC 형식이 아니라서 그대로 못 쓴다. dev는 기본값이 없어 비면 기동 실패 | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USERNAME` | 필수 | 아니오 | DB 사용자 | `${{Postgres.PGUSER}}` |
| `DB_PASSWORD` | 필수 | **예** | DB 비밀번호 | `${{Postgres.PGPASSWORD}}` |
| `PHONE_AES_KEY` | 필수 | **예** | 휴대폰 번호 암호화 키. Base64로 32바이트. 바꾸면 이미 저장된 번호를 못 읽는다 | `openssl rand -base64 32` 결과 |
| `PHONE_HMAC_KEY` | 필수 | **예** | 휴대폰 번호 중복 확인용 HMAC 키. Base64. 바꾸면 같은 번호로 다시 가입된다 | `openssl rand -base64 32` 결과 |
| `REMEMBER_ME_KEY` | 필수 | **예** | 로그인 상태 유지(`FREMEMBER`) 서명 키. 바꾸면 기존 토큰이 모두 무효 | `openssl rand -base64 32` 결과 |
| `COOKIE_SECURE` | 선택 | 아니오 | 쿠키 Secure 속성. dev 기본 `true` (HTTPS) | `true` |
| `COOKIE_DOMAIN` | 선택 | 아니오 | 쿠키 도메인. 비우면 host-only 쿠키. API와 프론트를 같은 최상위 도메인의 서브도메인으로 나눌 때만 넣는다 | (비움) |
| `ALLOWED_ORIGINS` | 선택 | 아니오 | CORS 허용 출처. 쉼표로 구분. 비우면 CORS 설정을 등록하지 않는다. 기본 `http://localhost:5173`. 프론트가 프록시(Vite, Vercel rewrite)로 붙으면 **빈 값으로 넣는다** (변수를 지우면 기본값이 쓰인다). 프록시가 브라우저의 `Origin`을 그대로 넘겨서, 목록에 없는 출처는 403 `Invalid CORS request`가 난다 | (빈 값) |
| `AI_MODE` | 선택 | 아니오 | `fake` 또는 `http`. dev 기본 `fake` | `fake` |
| `AI_BASE_URL` | `http`일 때 | 아니오 | AI 서버(FastAPI) 주소 | (`fake`면 비움) |
| `AI_INTERNAL_TOKEN` | `http`일 때 | **예** | `X-Internal-Token`. `http`인데 비면 기동 실패 | (`fake`면 비움) |
| `STORAGE_TYPE` | 선택 | 아니오 | 파일 저장소. `local`(기본) 또는 `s3`. 진짜 AI(`AI_MODE=http`)를 붙이면 `s3` (AI 서버와 컴퓨터가 달라 폴더를 같이 못 쓴다) | `local` |
| `STORAGE_LOCAL_ROOT` | 선택 | 아니오 | `local`일 때 그림 파일 폴더. 기본 `./data/storage` (이미지 안에서 `/app/data/storage`) | `/app/data/storage` |
| `STORAGE_S3_BUCKET` | `s3`일 때 | 아니오 | S3 버킷 이름. `s3`인데 비면 기동 실패. AI 서버와 같은 버킷 | (`local`이면 비움) |
| `STORAGE_S3_REGION` | 선택 | 아니오 | S3 리전. 기본 `ap-northeast-2`(서울) | (비움) |
| `AWS_ACCESS_KEY_ID` | `s3`일 때 | 아니오 | 그 버킷만 읽고 쓰는 IAM 사용자의 액세스 키 | (`local`이면 비움) |
| `AWS_SECRET_ACCESS_KEY` | `s3`일 때 | **예** | 위 액세스 키의 비밀 키 | (`local`이면 비움) |
| `SERVER_PORT` | 선택 | 아니오 | 서버 포트. 없으면 `PORT`, 둘 다 없으면 8080. Railway는 `PORT`를 넣어 주므로 비워 둔다 | (비움) |
| `MAX_REGENERATIONS` | 선택 | 아니오 | 다시 만들기 최대 횟수. 기본 1 (기획 확정) | (비움) |
| `FIRSTORY_AI_FAKE_STAGE_DURATION` | 선택 | 아니오 | 가짜 AI 생성 단계 시간. dev 기본 `40s`. 빠르게 보고 싶으면 줄인다 | `40s` |
| `JAVA_TOOL_OPTIONS` | 선택 | 아니오 | JVM 옵션. 이미지 기본 `-XX:MaxRAMPercentage=75`. 넣으면 기본값을 덮어쓴다 | (비움) |

비밀값은 저장소에 있는 로컬 기본값을 쓰지 않는다. dev는 키 세 개(`PHONE_AES_KEY`, `PHONE_HMAC_KEY`, `REMEMBER_ME_KEY`)에 기본값이 없어서 비면 기동하지 않는다.

넣지 않는 것: `firstory.sms.local-fixed-code`는 `application-dev.yml`에 이미 `000000`으로 있다. 고정 인증번호는 local, dev 프로필에서만 허용되고 다른 프로필(prod 등, 프로필 없음 포함)에 설정되면 기동이 실패한다.

## 그림 파일 (볼륨 또는 S3)

진짜 AI 서버를 붙이면(`AI_MODE=http`) `STORAGE_TYPE=s3`로 둔다. AI 서버가 쓴 그림을 Spring이 읽어야 하는데, Railway는 서비스마다 컴퓨터가 달라 볼륨을 같이 쓸 수 없다. S3를 쓰면 아래 볼륨은 필요 없다.

가짜 AI만 쓸 때(`local`): 가짜 AI가 만든 그림은 `STORAGE_LOCAL_ROOT`에 쓴다. Railway 컨테이너의 파일은 다시 배포하면 사라지므로, 지우면 이미 만든 동화의 그림이 404가 난다. 유지하려면 서비스에 **Volume**을 `/app/data/storage`로 붙인다.

이미지는 root가 아닌 `firstory` 사용자로 돈다. 볼륨을 붙였는데 그림 저장이 권한 오류로 실패하면 서비스 변수에 `RAILWAY_RUN_UID=0`을 넣는다 (Railway 볼륨은 root 소유로 붙는다).

## 쿠키와 프론트

세션 쿠키 `FSESSION`은 SameSite=Lax라서 **다른 사이트에서 fetch로 부르면 쿠키가 붙지 않는다.** `*.up.railway.app`은 서브도메인마다 다른 사이트로 취급된다. 프론트는 다음 중 하나로 붙인다.

- 로컬 프론트(Vite)에서 붙일 때: Vite 프록시로 `/api`를 Railway 주소로 보낸다 (`docs/frontend/README.md`의 프록시 방식, `target`만 Railway 주소로, `changeOrigin: true`). 브라우저 입장에서는 같은 출처라 CORS가 필요 없다. `localhost`는 http여도 Secure 쿠키를 받는다.
- 프론트도 배포할 때: 프론트 서버가 `/api`를 같은 방식으로 프록시하거나(Vercel은 `vercel.json`의 `rewrites`로 `/api/:path*`를 Railway 주소로), 둘을 같은 최상위 도메인의 서브도메인에 두고 `COOKIE_DOMAIN`을 맞춘다.

## 확인

배포 후:

```bash
curl https://<서비스 주소>/api/health          # {"status":"ok"}
```

브라우저에서 `https://<서비스 주소>/swagger-ui/index.html`을 연다. 사용 순서는 `CLAUDE.md`의 Swagger 절과 같다 (인증번호 `000000`).

## 로컬에서 이미지 확인

```bash
docker compose up -d                         # .env의 POSTGRES_PORT (예: 55432)
docker build -t firstory-backend:dev .
docker run --rm -p 8082:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e DB_URL=jdbc:postgresql://host.docker.internal:55432/firstory \
  -e DB_USERNAME=firstory -e DB_PASSWORD=firstory \
  -e PHONE_AES_KEY=$(openssl rand -base64 32) \
  -e PHONE_HMAC_KEY=$(openssl rand -base64 32) \
  -e REMEMBER_ME_KEY=$(openssl rand -base64 32) \
  -e COOKIE_SECURE=false \
  firstory-backend:dev
curl http://localhost:8082/api/health
```

Windows Git Bash에서는 `docker run` 앞에 `MSYS_NO_PATHCONV=1`을 붙인다. 붙이지 않으면 `/`로 시작하는 Base64 키를 Git Bash가 Windows 경로로 바꿔 `phone-aes-key는 Base64여야 해요`로 기동이 실패한다.

로컬 DB에 local 프로필로 가입한 회원이 있으면 키가 달라 그 회원의 휴대폰 번호는 읽지 못한다. 확인만 하고 새로 가입해서 쓴다. 로컬 http에서 Swagger로 로그인까지 해 보려면 `COOKIE_SECURE=false`로 띄운다.
