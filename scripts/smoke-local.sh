#!/usr/bin/env bash
# 로컬 서버에 전체 흐름을 curl로 한 번 돌려 본다 (docs/frontend/firstory.http와 같은 순서).
# Git Bash, macOS, Linux에서 돈다. curl만 있으면 된다 (jq 불필요).
#
# 1) 서버를 생성 단계 5초로 띄운다 (기본 40초면 생성만 2분 40초 걸린다)
#      FIRSTORY_AI_FAKE_STAGE_DURATION=5s ./gradlew bootRun --args='--spring.profiles.active=local'
#    PowerShell:
#      $env:FIRSTORY_AI_FAKE_STAGE_DURATION="5s"; .\gradlew.bat bootRun --args="--spring.profiles.active=local"
# 2) 다른 창에서
#      bash scripts/smoke-local.sh
#    진짜 AI 서버와 붙였으면 (FIRSTORY_AI_MODE=http): AI_MODE=http bash scripts/smoke-local.sh
#
# 환경 변수: BASE_URL(기본 http://localhost:8081), GENERATION_TIMEOUT(초, 기본 300),
#           AI_MODE(fake | http, 기본 fake. http면 가짜 AI만 만드는 샘플 제목은 확인하지 않는다)
#           인터뷰 질문 수는 0~5개 어느 쪽이든 통과한다 (진짜 AI는 질문 없이 바로 확인으로 넘어갈 수 있다)
# 성공하면 0, 실패하면 실패한 단계와 응답을 보여 주고 1로 끝난다.

set -u

BASE_URL="${BASE_URL:-http://localhost:8081}"
GENERATION_TIMEOUT="${GENERATION_TIMEOUT:-300}"
AI_MODE="${AI_MODE:-fake}"
CODE="000000"   # application-local.yml의 firstory.sms.local-fixed-code
PASSWORD="firstory123"
RUN_ID="$(date +%s)$RANDOM"
EMAIL="smoke${RUN_ID}@example.com"
PHONE="010-$(printf '%04d' $((RANDOM % 10000)))-$(printf '%04d' $((RANDOM % 10000)))"

WORK="$(mktemp -d)"
JAR="$WORK/cookies.txt"
BODY_FILE="$WORK/body"
trap 'rm -rf "$WORK"' EXIT

STATUS=""
BODY=""
STEP_NO=0
STARTED=$(date +%s)

step() {
    STEP_NO=$((STEP_NO + 1))
    printf '[%2d] %s ... ' "$STEP_NO" "$1"
}

ok() {
    echo "OK${1:+ ($1)}"
}

fail() {
    echo "FAIL"
    echo "  $1"
    echo "  HTTP $STATUS"
    echo "  $BODY" | head -c 2000
    echo
    exit 1
}

# XSRF-TOKEN 쿠키 값 (curl 쿠키 파일의 6번째 칸이 이름, 7번째가 값)
xsrf() {
    awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR" 2>/dev/null | tail -n 1
}

# call METHOD PATH [JSON]: STATUS와 BODY를 채운다. 상태를 바꾸는 요청에는 X-XSRF-TOKEN을 붙인다
call() {
    local method="$1" path="$2" json="${3:-}"
    local args=(-s -o "$BODY_FILE" -w '%{http_code}' -c "$JAR" -b "$JAR" -X "$method")
    if [ "$method" != "GET" ]; then
        args+=(-H "X-XSRF-TOKEN: $(xsrf)")
    fi
    if [ -n "$json" ]; then
        # 본문은 파일로 넘긴다. Windows Git Bash는 인자의 한글을 시스템 코드 페이지로 바꿔 UTF-8이 깨진다
        printf '%s' "$json" > "$WORK/request.json"
        args+=(-H 'Content-Type: application/json' --data-binary "@$WORK/request.json")
    fi
    STATUS="$(curl "${args[@]}" "$BASE_URL$path")" || STATUS="000"
    BODY="$(cat "$BODY_FILE" 2>/dev/null)"
}

expect() {
    [ "$STATUS" = "$1" ] || fail "HTTP $1을 기대했어요"
}

contains() {
    case "$BODY" in
        *"$1"*) ;;
        *) fail "응답에 $1 이(가) 없어요" ;;
    esac
}

# json_num KEY: 응답에서 처음 나오는 "KEY":숫자
json_num() {
    printf '%s' "$BODY" | sed -n "s/.*\"$1\":\([0-9][0-9]*\).*/\1/p" | head -n 1
}

# json_first_num KEY: 처음 나오는 "KEY":숫자 (sed의 탐욕 매칭을 피해 앞에서부터)
json_first_num() {
    printf '%s' "$BODY" | grep -o "\"$1\":[0-9]*" | head -n 1 | cut -d: -f2
}

json_first_str() {
    printf '%s' "$BODY" | grep -o "\"$1\":\"[^\"]*\"" | head -n 1 | cut -d'"' -f4
}

wait_for_reviewing() {
    local story="$1" label="$2" begin elapsed status stage
    begin=$(date +%s)
    while :; do
        call GET "$story/generation"
        expect 200
        status="$(json_first_str status)"
        stage="$(json_first_num stage)"
        elapsed=$(( $(date +%s) - begin ))
        case "$status" in
            REVIEWING) ok "$label ${elapsed}초"; return ;;
            FAILED) fail "생성이 실패했어요" ;;
        esac
        [ "$elapsed" -lt "$GENERATION_TIMEOUT" ] || fail "${GENERATION_TIMEOUT}초 안에 REVIEWING이 되지 않았어요 (status=$status, stage=$stage). 서버를 FIRSTORY_AI_FAKE_STAGE_DURATION=5s로 띄웠는지 확인해요"
        sleep 3
    done
}

echo "FIRSTORY smoke test → $BASE_URL"
echo "  email=$EMAIL phone=010-****-${PHONE: -4}"
echo

# 1. CSRF, 문자 인증, 가입, 내 정보
step "서버 응답과 CSRF 토큰 (GET /api/auth/csrf)"
call GET /api/auth/csrf
[ "$STATUS" != "000" ] || fail "서버에 연결할 수 없어요. bootRun으로 local 서버를 먼저 띄워요"
expect 204
[ -n "$(xsrf)" ] || fail "XSRF-TOKEN 쿠키가 없어요"
ok

step "CSRF 헤더 없이 보내면 403"
STATUS="$(curl -s -o "$BODY_FILE" -w '%{http_code}' -c "$JAR" -b "$JAR" -X POST "$BASE_URL/api/stories")"
BODY="$(cat "$BODY_FILE")"
expect 403
ok

step "인증번호 받기 (send-code)"
call POST /api/auth/phone/send-code "{\"phone\":\"$PHONE\",\"purpose\":\"SIGNUP\"}"
expect 200
contains '"expiresInSeconds":180'
ok

step "고정 인증번호 $CODE 확인 (verify)"
call POST /api/auth/phone/verify "{\"phone\":\"$PHONE\",\"code\":\"$CODE\"}"
expect 204
ok

step "가입 (signup)"
call POST /api/auth/signup "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"phone\":\"$PHONE\",\"consents\":{\"GUARDIAN\":true,\"TERMS\":true,\"PRIVACY\":true,\"SMS_NOTIFY\":true},\"termsVersion\":\"2026-10-01\"}"
expect 201
ok

step "내 정보 (GET /api/me)"
call GET /api/me
expect 200
contains "\"email\":\"$EMAIL\""
contains '"phoneMasked":"010-****-'
ok

# 2. 아이 프로필
CHILD_PROFILE='{"birthYm":"2021-03","gender":"FEMALE","traits":["SHY"],"interestCodes":["DINOSAUR"],"customInterests":["기차"]}'
step "아이 프로필 저장 (POST /api/children)"
call POST /api/children "$CHILD_PROFILE"
expect 201
CHILD_ID="$(json_first_num id)"
[ -n "$CHILD_ID" ] || fail "아이 id가 없어요"
ok "childId=$CHILD_ID"

# 3. 동화 만들기
step "새 동화 (POST /api/stories)"
call POST /api/stories
expect 201
STORY_ID="$(json_num storyId)"
[ -n "$STORY_ID" ] || fail "storyId가 없어요"
STORY="/api/stories/$STORY_ID"
ok "storyId=$STORY_ID"

step "상황 (PUT situation)"
call PUT "$STORY/situation" '{"timing":"PAST","category":"NEW_ENVIRONMENT","description":"새 어린이집에 갔어요","reactions":["AVOIDED"],"emotions":["SCARED"]}'
expect 200
contains '"action":"PROCEED"'
ok

step "아이 (PUT child, 저장된 프로필)"
call PUT "$STORY/child" "{\"childId\":$CHILD_ID,\"saveForNextTime\":false,\"profile\":$CHILD_PROFILE}"
expect 200
contains '"saved":true'
ok

step "전하고 싶은 말 (PUT message)"
call PUT "$STORY/message" '{"message":"처음엔 떨려도 괜찮다고 말해 주고 싶어요"}'
expect 200
contains '"saved":true'
ok

step "인터뷰 시작 (POST interview/start)"
call POST "$STORY/interview/start"
expect 200
# 진짜 AI는 입력만으로 충분하면 질문 없이 바로 CONFIRM을 준다 (스펙 7-2). 가짜 AI는 항상 질문 1부터
case "$BODY" in
    *'"type":"CONFIRM"'*)
        contains '"status":"CONFIRMING"'
        INTERVIEW_DONE=true
        ok "질문 없이 바로 확인"
        ;;
    *)
        contains '"status":"INTERVIEWING"'
        contains '"turn":1'
        INTERVIEW_DONE=false
        ok
        ;;
esac

step "인터뷰 답변 (CONFIRMING까지)"
ANSWERS=0
while [ "$INTERVIEW_DONE" = false ]; do
    call POST "$STORY/interview/answer" '{"answer":"문 앞에서 한참 서 있었어요"}'
    expect 200
    ANSWERS=$((ANSWERS + 1))
    case "$BODY" in
        *'"status":"CONFIRMING"'*) break ;;
    esac
    [ "$ANSWERS" -lt 5 ] || fail "답변 5번 뒤에도 인터뷰가 끝나지 않았어요"
done
ok "질문 ${ANSWERS}개"

step "만들기 전 확인 (GET draft)"
call GET "$STORY/draft"
expect 200
contains '"status":"CONFIRMING"'
contains "\"childId\":$CHILD_ID"
ok

# 4. 생성, 미리보기, 그림
step "동화 만들기 시작 (POST confirm)"
call POST "$STORY/confirm"
expect 202
contains '"status":"REQUESTED"'
ok

step "생성 진행 (GET generation, 3초 간격)"
wait_for_reviewing "$STORY" "처음 만들기"

step "미리보기 (GET preview)"
call GET "$STORY/preview"
expect 200
contains '"versionNo":1'
if [ "$AI_MODE" = fake ]; then
    contains '"title":"공룡 아로의 첫 인사"'   # 가짜 AI 샘플 (fake-ai/sample-story.json)
else
    contains '"title":"'
fi
contains '"regenerateRemaining":1'
VERSION1="$(printf '%s' "$BODY" | sed -n 's/.*"version":{"id":\([0-9][0-9]*\).*/\1/p')"
IMAGE_URL="$(json_first_str imageUrl)"
[ -n "$VERSION1" ] && [ -n "$IMAGE_URL" ] || fail "버전 id나 그림 주소가 없어요"
ok "versionId=$VERSION1"

step "그림 1장이 실제 PNG (GET $IMAGE_URL)"
HEADERS="$WORK/headers"
IMAGE="$WORK/page1.png"
STATUS="$(curl -s -D "$HEADERS" -o "$IMAGE" -w '%{http_code}' -b "$JAR" "$BASE_URL$IMAGE_URL")"
BODY="$(cat "$HEADERS")"
expect 200
grep -qi '^content-type: image/png' "$HEADERS" || fail "Content-Type이 image/png가 아니에요"
grep -qi '^x-content-type-options: nosniff' "$HEADERS" || fail "nosniff 헤더가 없어요"
MAGIC="$(head -c 8 "$IMAGE" | od -An -tx1 | tr -d ' \n')"
[ "$MAGIC" = "89504e470d0a1a0a" ] || fail "PNG 시그니처가 아니에요 ($MAGIC)"
ok "$(wc -c < "$IMAGE" | tr -d ' ')바이트, 시그니처 89 50 4E 47"

# 5. 그림만 다시, 버전 전환, 확정
step "그림만 다시 3쪽 (POST regenerate)"
call POST "$STORY/regenerate" '{"scope":"IMAGES","pages":[3]}'
expect 202
contains '"status":"REQUESTED"'
ok

step "다시 만들기 진행 (GET generation)"
wait_for_reviewing "$STORY" "그림만 다시"

step "버전 2 미리보기"
call GET "$STORY/preview"
expect 200
contains '"versionNo":2'
[ "$AI_MODE" = fake ] && contains '(다시 1)'   # 가짜 AI만 다시 만든 제목 뒤에 붙인다
contains '"regenerateRemaining":0'
ok

step "버전 목록 (GET versions)"
call GET "$STORY/versions"
expect 200
contains '"versionNo":1'
contains '"versionNo":2'
ok

step "버전 1로 되돌리기 (PUT current-version)"
call PUT "$STORY/current-version" "{\"versionId\":$VERSION1}"
expect 200
contains '"versionNo":1'
ok

step "확정 (POST approve)"
call POST "$STORY/approve"
expect 200
contains '"status":"COMPLETED"'
ok

# 6. 홈, 서재, 상세, 가이드
step "홈 (GET /api/home)"
call GET /api/home
expect 200
contains '"unread":1'
ok

step "서재 UNREAD에 있음"
call GET "/api/stories?filter=UNREAD"
expect 200
contains "\"id\":$STORY_ID"
ok

step "동화 상세 (GET story)"
call GET "$STORY"
expect 200
contains '"canRead":true'
contains '"readCount":0'
ok

step "대화 가이드 (GET guide)"
call GET "$STORY/guide"
expect 200
PROMPT_ID="$(printf '%s' "$BODY" | sed -n 's/.*"prompts":\[{"id":\([0-9][0-9]*\).*/\1/p')"
[ -n "$PROMPT_ID" ] || fail "대화 질문 id가 없어요"
ok "promptId=$PROMPT_ID"

# 7. 함께 읽기
step "읽기 화면 내용 (GET book)"
call GET "$STORY/book"
expect 200
contains "\"versionId\":$VERSION1"
ok

step "읽기 시작 (POST reading-sessions)"
call POST "$STORY/reading-sessions"
expect 201
SESSION_ID="$(json_num sessionId)"
[ -n "$SESSION_ID" ] || fail "sessionId가 없어요"
SESSION="/api/reading-sessions/$SESSION_ID"
ok "sessionId=$SESSION_ID"

step "쪽 넘김 1~10 (PUT progress) + 대화 버튼 (PUT dialogue)"
for page in 1 2 3 4 5 6 7 8 9 10; do
    call PUT "$SESSION/progress" "{\"page\":$page}"
    expect 204
    if [ "$page" = 4 ]; then
        call PUT "$SESSION/dialogue/$PROMPT_ID" '{"action":"TALKED"}'
        expect 204
    fi
done
ok

step "다 읽음 (POST complete)"
call POST "$SESSION/complete"
expect 204
ok

step "서재 READ로 옮겨짐"
call GET "/api/stories?filter=READ"
expect 200
contains "\"id\":$STORY_ID"
call GET "/api/stories?filter=UNREAD"
expect 200
contains '"items":[]'
ok

# 8. 로그아웃
step "로그아웃 (POST /api/auth/logout)"
call POST /api/auth/logout
expect 204
ok

step "로그아웃 뒤 내 정보는 401"
call GET /api/me
expect 401
contains '"code":"UNAUTHORIZED"'
ok

echo
echo "모든 단계 통과 (${STEP_NO}단계, $(( $(date +%s) - STARTED ))초)"
