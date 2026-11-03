#!/usr/bin/env bash
# EC2 개발 서버 업데이트 배포. 설명은 docs/deploy/ec2.md
#
#   deploy.sh backend   # Backend_Mainpage를 받아 전부 다시 빌드 (compose·Caddyfile이 바뀌었을 수 있다)
#   deploy.sh ai        # AI_worker를 받아 ai만 다시 빌드
#
# GitHub Actions가 SSM Run Command(root)로 부른다. 서버에서 직접 실행해도 된다.
# git은 저장소 주인(ubuntu)으로 돌리고, 두 저장소 배포가 겹치지 않게 잠근다.
set -euo pipefail

TARGET="${1:-}"
HERE="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$(cd "$HERE/../.." && pwd)"
AI_DIR="$(cd "$BACKEND_DIR/../AI_worker" && pwd)"
OWNER="$(stat -c %U "$BACKEND_DIR")"

case "$TARGET" in
    backend) REPO="$BACKEND_DIR"; SERVICES=() ;;
    ai) REPO="$AI_DIR"; SERVICES=(ai) ;;
    *) echo "사용법: deploy.sh backend|ai" >&2; exit 2 ;;
esac

exec 9>/tmp/firstory-deploy.lock
flock -w 1800 9

as_owner() {
    if [ "$(id -un)" = "$OWNER" ]; then "$@"; else sudo -u "$OWNER" -H "$@"; fi
}

echo "== $TARGET: git pull"
as_owner git -C "$REPO" pull --ff-only
as_owner git -C "$REPO" log --oneline -1

cd "$HERE"
echo "== docker compose up -d --build ${SERVICES[*]:-(전부)}"
docker compose up -d --build "${SERVICES[@]}" 2>&1 | tail -n 40

# 헬스 체크: 5초마다 최대 5분
wait_for() {
    local name="$1"; shift
    for _ in $(seq 60); do
        if "$@" >/dev/null 2>&1; then echo "== $name ok"; return 0; fi
        sleep 5
    done
    echo "== $name 헬스 체크 실패" >&2
    docker compose logs --tail 50 "$name" >&2
    return 1
}

ai_health() {
    docker compose exec -T ai python -c 'import os, urllib.request as u
u.urlopen(u.Request("http://localhost:8000/health", headers={"X-Internal-Token": os.environ["FIRSTORY_INTERNAL_TOKEN"]}), timeout=5)'
}

backend_health() {
    local domain
    domain="$(grep -E '^API_DOMAIN=' .env | cut -d= -f2-)"
    curl -fsS --max-time 5 --resolve "$domain:443:127.0.0.1" "https://$domain/api/health"
}

wait_for ai ai_health
[ "$TARGET" = ai ] || wait_for backend backend_health

docker image prune -f >/dev/null   # 다시 빌드할 때마다 쌓이는 이름 없는 이미지
docker compose ps --format '{{.Service}} {{.Status}}'
