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
- dev 프로필이다. 메일은 `MAIL_MODE=smtp`(기본, 팀 Gmail)면 실제로 보내고 인증번호도 무작위다. `MAIL_MODE=log`면 보내지 않고 인증번호가 `000000`이라 누구나 가입할 수 있으니 **실제 개인정보를 넣지 않는다.**

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
- 메일: `MAIL_USERNAME`은 팀 Gmail 주소, `MAIL_PASSWORD`는 그 계정에 2단계 인증을 켜고 [앱 비밀번호](https://myaccount.google.com/apppasswords)에서 만든 16자리. 둘 중 하나라도 비면 `MAIL_MODE=smtp`라 기동하지 않는다. `MAIL_FROM`은 비우면 `MAIL_USERNAME`. EC2는 587 포트로 보내는 데 막힘이 없다.
- 카카오: `KAKAO_REST_API_KEY`는 카카오 디벨로퍼스 [앱 → 앱 키]의 REST API 키(비면 기동하지 않는다). [카카오 로그인 → 보안]에서 Client Secret을 켰으면 `KAKAO_CLIENT_SECRET`도 넣는다. `KAKAO_REDIRECT_URIS`는 카카오에 등록한 프론트 redirect URI(쉼표로 여러 개). 카카오 없이 띄우려면 `KAKAO_MODE=fake`(아무 code로 가짜 회원이 된다. 실제 서비스에서는 쓰지 않는다).
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

## 백업

DB는 EC2 안 컨테이너라 인스턴스를 잃으면 같이 잃는다. 하루 한 번 덤프를 남긴다(`crontab -e`).

```
0 4 * * * cd /home/ubuntu/firstory/Backend_Mainpage/deploy/ec2 && mkdir -p ~/backups && docker compose exec -T postgres pg_dump -U firstory -d firstory --format=custom > ~/backups/firstory-$(date +\%F).dump && find ~/backups -name 'firstory-*.dump' -mtime +7 -delete
```

인스턴스 밖에 두려면 이 파일을 S3로 올린다(역할에 백업 경로 `PutObject` 추가 필요).
