# EC2 개발 서버 배포

Railway에서 옮긴 **개발 서버(dev 프로필)** 다. EC2 한 대에 docker compose로 네 개를 띄운다(`deploy/ec2/`).

```
인터넷 ─443─▶ caddy (HTTPS, Let's Encrypt) ─▶ backend (Spring, dev) ─▶ postgres
                                                     │
                                                     └─ http://ai:8000 ─▶ ai (AI 워커)
backend, ai ─▶ S3 버킷 (그림, EC2 IAM 역할)
```

- 공개 포트는 Caddy의 80, 443뿐이다. Postgres와 AI 워커는 compose 내부망에서만 보인다.
- 그림은 Railway 때와 같은 S3 버킷을 쓴다. 접근 키 대신 EC2 IAM 역할로 읽고 쓴다.
- 프론트(Vercel)는 지금처럼 `/api`를 프록시한다. `vercel.json`의 주소만 이 서버로 바꾼다. 그래서 `ALLOWED_ORIGINS`는 비운다(`docs/deploy/railway.md`의 "쿠키와 프론트").
- dev 프로필이다. 메일은 `MAIL_MODE=log`(compose 기본)면 보내지 않고 인증번호가 `000000`이라 누구나 가입할 수 있으니 **실제 개인정보를 넣지 않는다.** `.env`에 `MAIL_MODE=smtp`(팀 Gmail)를 넣으면 실제로 보내고 인증번호도 무작위다. 카카오도 compose 기본은 `KAKAO_MODE=fake`이고, 진짜 카카오는 `.env`에 `KAKAO_MODE=http`를 넣는다. 키가 없는 서버가 배포 뒤 기동하지 않는 일을 막으려고 기본값을 이렇게 둔다(#47).

## 1. AWS 준비 (콘솔, 서울 리전 `ap-northeast-2`)

### IAM 역할

EC2에 붙일 역할(예: `firstory-ec2`)을 만든다. 신뢰 대상은 EC2이고, 인라인 정책은 그 버킷만 허용한다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": ["s3:GetObject", "s3:PutObject"], "Resource": "arn:aws:s3:::<버킷>/*" },
    { "Effect": "Allow", "Action": "s3:ListBucket", "Resource": "arn:aws:s3:::<버킷>" }
  ]
}
```

`s3:ListBucket`이 없으면 없는 그림이 404 대신 403이 되어 그림 API가 500을 낸다.

### EC2 인스턴스

| 항목 | 값 |
|---|---|
| AMI | Ubuntu Server 24.04 LTS |
| 유형 | `t3.medium`(4GB). Spring 1.5GB, AI 1GB, Postgres 512MB에 Gradle 빌드까지 한 대에서 한다. `t3.small`(2GB)은 빌드 중 메모리가 모자랄 수 있다 |
| 저장소 | gp3 30GB (Docker 이미지가 합쳐 약 3GB) |
| IAM 인스턴스 프로파일 | 위 역할 |
| 보안 그룹 | 22(내 IP만), 80, 443(어디서나). 8080, 8000, 5432는 열지 않는다 |
| 메타데이터 | IMDSv2 필수, **응답 홉 제한 2** |

**홉 제한을 2로 바꿔야 한다.** 기본값 1이면 컨테이너 안에서 메타데이터(IAM 역할 자격 증명)에 닿지 못해 S3가 `Unable to load credentials`로 실패한다. 콘솔 → 인스턴스 → 작업 → 인스턴스 설정 → 인스턴스 메타데이터 옵션 수정, 또는

```bash
aws ec2 modify-instance-metadata-options --instance-id <id> --http-tokens required --http-put-response-hop-limit 2
```

### 고정 IP와 도메인

1. 탄력적 IP(Elastic IP)를 할당해 인스턴스에 연결한다. 인스턴스를 다시 시작해도 주소가 바뀌지 않는다.
2. 도메인 DNS에 A 레코드를 만든다. 예: `api.<도메인>` → 탄력적 IP. Caddy가 이 이름으로 인증서를 받는다.
3. `dig +short api.<도메인>`이 탄력적 IP를 돌려주는지 확인한 뒤 띄운다. DNS가 퍼지기 전에 띄우면 인증서 발급이 실패하고, 여러 번 실패하면 Let's Encrypt가 한동안 막는다.

## 2. 서버 준비

```bash
ssh ubuntu@<탄력적 IP>

# Docker (공식 저장소)
sudo apt-get update && sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt-get update && sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker ubuntu && exit   # 다시 접속해야 docker 그룹이 적용된다

# 두 저장소를 같은 폴더에 나란히 (비공개 저장소면 gh auth login 또는 배포 키)
mkdir -p ~/firstory && cd ~/firstory
git clone https://github.com/FirstoryAI/Backend_Mainpage.git
git clone https://github.com/FirstoryAI/AI_worker.git
```

`deploy/ec2/.env`의 `AI_DIR` 기본값(`../../../AI_worker`)이 이 배치를 가정한다.

## 3. 환경 변수

```bash
cd ~/firstory/Backend_Mainpage/deploy/ec2
cp .env.example .env && chmod 600 .env && vi .env
```

- `REMEMBER_ME_KEY`는 **Railway 백엔드 서비스와 같은 값**을 넣는다. 다르면 로그인 유지 토큰이 모두 무효가 된다. (휴대폰 번호는 V12부터 받지 않아 `PHONE_AES_KEY`, `PHONE_HMAC_KEY`는 더 쓰지 않는다. `.env`에 남아 있어도 된다.)
- 메일: `MAIL_USERNAME`은 팀 Gmail 주소, `MAIL_PASSWORD`는 그 계정에 2단계 인증을 켜고 [앱 비밀번호](https://myaccount.google.com/apppasswords)에서 만든 16자리. `MAIL_MODE=smtp`와 함께 넣는다. smtp인데 둘 중 하나라도 비면 기동하지 않는다. `MAIL_FROM`은 비우면 `MAIL_USERNAME`. EC2는 587 포트로 보내는 데 막힘이 없다.
- 메일 디자인(HTML): `.env`에 `APP_URL`(메일 버튼이 여는 프론트 주소, 예 `https://www.도메인`)을 넣는다. 그림 주소는 compose가 `https://${API_DOMAIN}/mail`로 만든다. `APP_URL`이 비면 글자 메일만 간다
- 카카오: `KAKAO_REST_API_KEY`는 카카오 디벨로퍼스 [앱 → 앱 키]의 REST API 키. `KAKAO_MODE=http`와 함께 넣는다(http인데 비면 기동하지 않는다). [카카오 로그인 → 보안]에서 Client Secret을 켰으면 `KAKAO_CLIENT_SECRET`도 넣는다. `KAKAO_REDIRECT_URIS`는 카카오에 등록한 프론트 redirect URI(쉼표로 여러 개). `KAKAO_MODE`를 넣지 않으면 fake(아무 code로 가짜 회원이 된다. 실제 서비스에서는 쓰지 않는다).
- `OPENAI_API_KEY`, `S3_BUCKET`도 Railway AI 서비스와 같은 값이다. `AI_INTERNAL_TOKEN`은 새로 만들어도 된다(Spring과 AI가 같은 `.env`를 읽는다).
- `POSTGRES_IMAGE`는 Railway DB와 같은 메이저 버전으로 한다(Railway DB에서 `SELECT version();`, 11/2 기준 18.6). 더 높은 버전의 `pg_dump` 파일은 낮은 버전에 복원되지 않을 수 있다. 18부터 공식 이미지의 데이터 위치가 바뀌어 compose는 볼륨을 `/var/lib/postgresql`에 붙인다. 16 이하 이미지를 쓰면 볼륨 경로를 `/var/lib/postgresql/data`로 되돌린다.
- AWS 접근 키는 넣지 않는다(IAM 역할).

## 4. Railway DB 옮기기

Spring을 처음 띄우기 **전에** 복원한다. Spring이 먼저 뜨면 Flyway가 빈 스키마를 만들어 복원과 부딪친다.

```bash
cd ~/firstory/Backend_Mainpage/deploy/ec2
docker compose up -d postgres

# Railway Postgres의 공개 연결 주소 (Railway → Postgres → Connect → Public Network, DATABASE_PUBLIC_URL)
read -s RAILWAY_DB_URL
docker run --rm postgres:18 pg_dump --format=custom --no-owner --no-acl "$RAILWAY_DB_URL" > firstory.dump
docker compose exec -T postgres pg_restore -U firstory -d firstory --no-owner --no-acl < firstory.dump
docker compose exec postgres psql -U firstory -d firstory -c "select max(version) from flyway_schema_history; select count(*) from stories;"
rm firstory.dump
```

- 덤프를 뜬 뒤 Railway에 쓰인 데이터는 넘어오지 않는다. 프론트 주소를 바꾸기 직전에 한 번 더 떠서 옮기거나(DB를 지우고 다시 복원), 개발 서버라 그 사이 데이터는 버린다.
- 동화 id가 이어지므로 S3 그림 key(`stories/{id}/...`)가 겹치지 않는다. **DB를 새로 시작하면 id가 1부터 다시 나와 옛 그림 key를 덮어쓴다.** 그럴 때는 버킷을 비우거나 새 버킷을 쓴다.
- AI 체크포인트는 옮기지 않는다(Railway AI에 볼륨이 없었다). 그래서 옮기기 전에 인터뷰 중이거나 미리보기 중이던 동화는 다시 만들기·다시 시도가 409 `AI_STATE_MISMATCH`가 된다. 확정(`approve`)과 확정된 동화 읽기는 된다.

## 5. 띄우기

```bash
docker compose up -d --build          # 처음에는 Gradle·uv 빌드로 5~10분
docker compose ps
docker compose logs -f backend        # Started FirstoryApplication
docker compose logs caddy | grep -i certificate   # certificate obtained successfully
curl https://api.<도메인>/api/health   # {"status":"ok"}
```

## 6. 확인

로컬 PC에서 진짜 AI로 smoke를 돌린다. 가입부터 확정까지 35단계를 돌고, 그림이 S3에서 WebP로 오는지도 본다. 진짜 OpenAI를 써서 한 번에 약 $0.5가 든다.

```bash
BASE_URL=https://api.<도메인> AI_MODE=http bash scripts/smoke-local.sh
```

그다음 프론트의 `vercel.json`에서 `/api/:path*`의 `destination`을 `https://api.<도메인>/api/:path*`로 바꾼다(프론트 저장소, 팀원). 문제가 생기면 이 줄만 Railway 주소로 되돌린다. Railway는 EC2가 안정될 때까지 지우지 않는다.

## 업데이트 배포

main에 합치면 GitHub Actions(`.github/workflows/deploy-ec2.yml`, AI_worker 저장소에도 같은 이름)가 자동으로 배포한다. 서버에서 직접 할 때는 같은 스크립트를 쓴다.

```bash
bash ~/firstory/Backend_Mainpage/deploy/ec2/deploy.sh backend   # 백엔드 받기 → 전부 다시 빌드
bash ~/firstory/Backend_Mainpage/deploy/ec2/deploy.sh ai        # AI 워커 받기 → ai만 다시 빌드
```

`deploy.sh`는 그 저장소를 `git pull --ff-only`(저장소 주인 ubuntu로) → `docker compose up -d --build` → 헬스 체크(AI `/health`, 백엔드면 `/api/health`까지, 5분)를 한다. 두 저장소 배포가 겹치면 `flock`으로 차례를 기다린다.

### 자동 배포 (GitHub Actions + SSM)

Actions는 SSH를 쓰지 않는다. GitHub OIDC로 AWS 역할을 받아 **SSM Run Command**로 서버에서 `deploy.sh`를 실행하고, 끝날 때까지 기다려 출력을 보여 준다. 그래서 22번은 내 IP에만 열어 두면 된다. AWS 키를 GitHub에 저장하지 않는다.

- 끄기(데모 중): 두 저장소의 Settings → Secrets and variables → Actions → Variables에서 `EC2_DEPLOY_ENABLED`를 `false`로. main에 합쳐도 배포하지 않는다. Actions 탭의 수동 실행(Run workflow)은 된다
- 수동 배포: Actions 탭 → EC2 배포 → Run workflow (main)
- 실패하면 Actions 로그에 서버 출력과 실패한 서비스의 마지막 로그 50줄이 나온다

처음 한 번 할 것 (AWS 콘솔, 계정 주인)

1. EC2 역할 `firstory-ec2`에 AWS 관리형 정책 `AmazonSSMManagedInstanceCore`를 붙인다. 서버의 SSM 에이전트(snap `amazon-ssm-agent`)는 Ubuntu 이미지에 들어 있다. 붙이고 몇 분 뒤 Systems Manager → Fleet Manager에 인스턴스가 보이면 된다
2. IAM → 자격 증명 공급자 → 공급자 추가: OpenID Connect, URL `https://token.actions.githubusercontent.com`, 대상 `sts.amazonaws.com`
3. IAM 역할 `firstory-github-deploy`를 만든다 (웹 자격 증명, 위 공급자). 신뢰 정책은 두 저장소의 main만 믿는다.
   두 저장소는 GitHub의 immutable subject를 써서 `sub`에 조직·저장소 번호가 붙는다(`repo:FirstoryAI@<조직 번호>/<저장소>@<저장소 번호>:ref:refs/heads/main`). `repo:FirstoryAI/...`로 쓰면 `Not authorized to perform sts:AssumeRoleWithWebIdentity`가 난다. 값은 `gh api repos/FirstoryAI/<저장소>/actions/oidc/customization/sub`의 `sub_claim_prefix`

    ```json
    {
      "Version": "2012-10-17",
      "Statement": [{
        "Effect": "Allow",
        "Principal": {"Federated": "arn:aws:iam::<계정 ID>:oidc-provider/token.actions.githubusercontent.com"},
        "Action": "sts:AssumeRoleWithWebIdentity",
        "Condition": {
          "StringEquals": {
            "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
            "token.actions.githubusercontent.com:sub": [
              "repo:FirstoryAI@338010585/Backend_Mainpage@1428046423:ref:refs/heads/main",
              "repo:FirstoryAI@338010585/AI_worker@1401639206:ref:refs/heads/main"
            ]
          }
        }
      }]
    }
    ```

    권한(인라인 정책)은 이 인스턴스에 `AWS-RunShellScript`를 보내고 결과를 보는 것만

    ```json
    {
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Action": "ssm:SendCommand",
          "Resource": [
            "arn:aws:ec2:ap-northeast-2:<계정 ID>:instance/<인스턴스 ID>",
            "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript"
          ]
        },
        {
          "Effect": "Allow",
          "Action": ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"],
          "Resource": "*"
        }
      ]
    }
    ```

4. 두 저장소의 Actions 변수(Variables, 비밀값 아님): `AWS_DEPLOY_ROLE_ARN`(위 역할 ARN), `EC2_INSTANCE_ID`

- AI 워커를 다시 띄우면 그 순간 돌던 생성은 FAILED가 된다(부모가 다시 시도하면 체크포인트에서 이어 간다). 인터뷰와 미리보기 중인 동화는 볼륨의 체크포인트로 이어 간다. **데모 중에는 다시 배포하지 않는다.**
- `docker compose down -v`는 볼륨(DB, 체크포인트, 인증서)까지 지운다. 쓰지 않는다.

## 베타 초대와 무료 이용권

동화 한 편을 AI로 처음 만들 때(생성 확정) 이용권 1회를 쓴다. 다시 만들기·다시 시도는 쓰지 않는다.

| 가입 경로 | 무료 | 설정 |
|---|---|---|
| 초대 링크 (공용 코드, 선착순) | `closes_at` 전에 가입하면 `credit_amount`회, `credit_expires_at`까지 | `invite_codes` 행 |
| 사전 연락 이메일 | `closes_at` 전에 가입·로그인하면 `credit_amount`회, `credit_expires_at`까지 | `pre_contacts` 행 |
| 개발용 이메일 | 무제한 | `.env`의 `UNLIMITED_EMAILS` |
| 일반 가입·기존 회원 | 1회, 기한 없음 | (자동) |

무료는 한 종류만 받는다(초대·사전 연락 2회면 일반 1회는 없다). `closes_at`(받을 수 있는 마감)이 지나면 초대 링크는 닫히고, 아직 받지 않은 사전 연락 이메일은 일반 가입·일반 회원(무료 1회)으로 본다. `credit_expires_at`(쓸 수 있는 기한)이 지나면 받은 이용권은 남아도 쓸 수 없다. `.env`의 `BETA_INVITE_ONLY=true`면 초대 링크·사전 연락·개발용 이메일만 가입할 수 있다. 베타가 끝나면 `false`로 바꾸고 `docker compose up -d`.

DB 작업은 compose 폴더에서 `docker compose exec postgres psql -U firstory firstory`로 들어가서 한다. 시각 뒤의 `+09`는 한국 시간이라는 뜻이라 꼭 붙인다.

초대 코드 만들기

```sql
-- 1) 추측하기 어려운 코드를 하나 만든다 (결과를 복사해 아래에 넣는다)
SELECT substr(md5(random()::text || clock_timestamp()::text), 1, 12) AS code;

-- 2) 선착순 100명, 링크 마감 11/22 23:59, 받은 무료 2회는 11/30 23:59까지
INSERT INTO invite_codes (code, max_uses, closes_at, credit_amount, credit_expires_at, created_at, updated_at)
VALUES ('여기에코드', 100, '2026-11-22 23:59:59+09', 2, '2026-11-30 23:59:59+09', now(), now());

-- 보낼 링크: https://firstoryofficial.com/api/invite/여기에코드
```

사전 연락 이메일 등록 (이미 회원이면 다음에 앱을 열 때 받는다). 11/22 23:59까지 가입·로그인하면 2회, 12/31 23:59까지 쓴다.

```sql
INSERT INTO pre_contacts (email, credit_amount, closes_at, credit_expires_at, created_at, updated_at)
VALUES
  (lower('kim@example.com'), 2, '2026-11-22 23:59:59+09', '2026-12-31 23:59:59+09', now(), now()),
  (lower('lee@example.com'), 2, '2026-11-22 23:59:59+09', '2026-12-31 23:59:59+09', now(), now())
ON CONFLICT (email) DO NOTHING;
```

사전 연락 안내 메일 (한 번만, `pre_contacts`를 넣은 뒤)

`deploy/ec2/pre-contact-mail/send.py`가 `pre_contacts` 중 받을 수 있는 마감(`closes_at`) 전인 이메일에 안내 메일을 보낸다. 아직 회원이 아니면 가입 안내(`signup.html`), 회원이면 로그인 안내(`login.html`). 보내는 계정은 `.env`의 `MAIL_USERNAME`·`MAIL_PASSWORD`(인증번호 메일과 같은 팀 Gmail), 그림은 `https://{API_DOMAIN}/mail`, 버튼은 `{APP_URL}/auth`. compose 폴더(`deploy/ec2`)에서 실행한다(python3만 필요).

```bash
python3 pre-contact-mail/send.py preview              # /tmp/firstory-mail-preview/에 HTML만 만든다
python3 pre-contact-mail/send.py test 내메일@gmail.com  # 두 종류를 내 메일로만 한 통씩 ([테스트] 제목)
python3 pre-contact-mail/send.py send                 # 받는 사람(가린 이메일)과 수를 보여 주고 y면 보낸다
```

보낸 주소는 `pre-contact-mail/sent.log`에 이메일의 SHA-256만 남겨, 다시 실행해도 같은 사람에게 두 번 보내지 않는다(실패한 주소만 다시 보낸다). `sent.log`는 커밋하지 않는다. 문구를 바꾸면 템플릿 HTML과 `send.py`의 글자 메일(`TEXT`)을 함께 고친다.

날짜 바꾸기. 받을 수 있는 마감(`closes_at`)과 쓸 수 있는 기한(`credit_expires_at`)은 따로 바꾼다. 쓸 수 있는 기한은 이미 받은 이용권(`story_credits.expires_at`)도 함께 바꾼다.

```sql
BEGIN;
-- 초대: 링크 마감 / 앞으로 받을 이용권 기한
UPDATE invite_codes
SET closes_at = '2026-11-22 23:59:59+09', credit_expires_at = '2026-11-30 23:59:59+09', updated_at = now()
WHERE code = '여기에코드';
-- 초대: 이미 받은 이용권 기한
UPDATE story_credits SET expires_at = '2026-11-30 23:59:59+09', updated_at = now()
WHERE invite_code_id = (SELECT id FROM invite_codes WHERE code = '여기에코드');
-- 사전 연락: 받을 수 있는 마감 / 앞으로 받을 이용권 기한
UPDATE pre_contacts
SET closes_at = '2026-11-22 23:59:59+09', credit_expires_at = '2026-12-31 23:59:59+09', updated_at = now();
-- 사전 연락: 이미 받은 이용권 기한
UPDATE story_credits SET expires_at = '2026-12-31 23:59:59+09', updated_at = now() WHERE source = 'PRE_CONTACT';
COMMIT;
```

초대 링크 바로 닫기

```sql
UPDATE invite_codes SET closes_at = now(), updated_at = now() WHERE code = '여기에코드';
```

현황

```sql
-- 초대 코드: 몇 명 가입했는지
SELECT code, used_count, max_uses, closes_at, credit_expires_at FROM invite_codes;

-- 사전 연락: 누가 가입해서 받아 갔는지
SELECT p.email, p.granted_at, u.created_at AS joined_at
FROM pre_contacts p LEFT JOIN users u ON u.id = p.granted_user_id
ORDER BY p.granted_at NULLS LAST;

-- 가입 경로별 인원
SELECT signup_source, count(*) FROM users WHERE deleted_at IS NULL GROUP BY signup_source;

-- 회원별 남은 무료 횟수 (개발용 이메일은 무제한이라 이 값과 상관없다)
SELECT u.email, u.signup_source, u.created_at,
       (SELECT coalesce(sum(greatest(c.amount - (SELECT count(*) FROM story_credit_uses cu
                                                 WHERE cu.credit_id = c.id), 0)), 0)
        FROM story_credits c
        WHERE c.user_id = u.id AND (c.expires_at IS NULL OR c.expires_at > now())) AS remaining
FROM users u
WHERE u.deleted_at IS NULL
ORDER BY u.created_at;

-- 초대 링크로 가입한 사람
SELECT u.email, u.created_at
FROM users u JOIN story_credits c ON c.user_id = u.id
WHERE c.invite_code_id = (SELECT id FROM invite_codes WHERE code = '여기에코드')
ORDER BY u.created_at;
```

초대 표시는 로그인 세션(`FSESSION`)에 남아서, 링크를 누른 브라우저에서 30분 넘게 아무 요청이 없으면 사라진다. 그때는 링크를 다시 누르면 된다.

사전 연락 이메일로 이미 가입한 회원은 앱을 열 때(`GET /api/me`) 이용권을 받으므로, 그 전에는 위 SQL의 남은 횟수가 일반 1회로 보인다.

## 백업

DB는 EC2 안 컨테이너라 인스턴스를 잃으면 같이 잃는다. 하루 한 번 덤프를 남긴다(`crontab -e`).

```
0 4 * * * cd /home/ubuntu/firstory/Backend_Mainpage/deploy/ec2 && mkdir -p ~/backups && docker compose exec -T postgres pg_dump -U firstory -d firstory --format=custom > ~/backups/firstory-$(date +\%F).dump && find ~/backups -name 'firstory-*.dump' -mtime +7 -delete
```

인스턴스 밖에 두려면 이 파일을 S3로 올린다(역할에 백업 경로 `PutObject` 추가 필요).
