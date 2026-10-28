# 가짜 AI와 진짜 AI

local 프로필은 AI 서버 없이 **가짜 AI**(`FakeAiClient`)로 돈다. 흐름과 응답 모양은 진짜와 같고, 속도와 질문 수도 진짜처럼 흉내 낸다. 그래서 가짜로 만든 화면은 진짜 AI를 붙여도 그대로 동작해야 한다.

## 차이와 대비할 것

| 항목 | 가짜 (local 예시값) | 진짜 | 프론트가 대비할 것 |
|---|---|---|---|
| 인터뷰 응답 | 2초 | 수 초 | 로딩 표시, **중복 클릭 방지** (겹치면 `AI_BUSY`) |
| 글 검사(위기 신호) | 1초 | 1~2초 | 다음 버튼 로딩 표시 |
| 생성 | 약 2분 40초 (단계당 40초 × 4) | 3~4분 | GEN-01 진행 화면, "나가도 계속 만들어요" 안내 |
| 질문 수 | 동화마다 1~5개 | 0~5개 (시작하자마자 CONFIRM일 수 있다) | **고정 개수 가정 금지** ("3/5" 같은 표시 X) |
| 답변 카드 `choices` | 질문마다 정해진 3개, 5번째 질문은 없음 | 질문마다 0~4개 | 빈 목록이면 카드를 숨긴다 |
| 그림 실패 | `[[PAGE_FAIL]]` | 가끔 | `imageStatus: FAILED` → 빈 자리, 그림만 다시 |
| AI 장애 | `[[AI_DOWN]]` | 가끔 | `AI_UNAVAILABLE`, `AI_BUSY` 안내 후 다시 시도 |
| 생성 실패 | `[[FAIL]]` | 가끔 | ST-03, 다시 시도 |
| 위기 신호 | 표시로 흉내 | 글의 의미로 판단 | 흐름은 같다 (`safety.action`) |
| 동화 내용 | 항상 같은 샘플 "공룡 아로의 첫 인사" | 입력에 맞게 | 글 길이가 쪽마다 다르다. `**의성어**` 표시 |
| 다시 만들기 | 제목 뒤에 `(다시 1)`, `(다시 2)`… | 내용이 바뀜 | 버전 구분 |
| 서버 재시작 | 진행 중이던 인터뷰·생성을 잃음 (`AI_STATE_MISMATCH`) | 이어 감 | 로컬에서는 새 동화를 만든다 |

## 표시 (마커)

입력 글에 아래 표시를 넣으면 그 상황을 흉내 낸다. 표시는 그대로 저장되니 개발용 동화에만 쓴다.

| 표시 | 넣는 곳 | 동작 |
|---|---|---|
| `[[SENSITIVE]]` | 글을 받는 곳 어디든 (상황 설명, 직접 입력, 전하고 싶은 말, 인터뷰 답변, 다시 만들기 메모) | `safety.action: CHOOSE_TONE` → ST-04 (분위기를 이미 골랐으면 PROCEED) |
| `[[REVISE]]` | 같은 곳 | 첫 번째는 `ASK_REVISE`(저장 안 함), 같은 동화에서 두 번째는 `BLOCK`(helpKind `PARENTING`) |
| `[[CRISIS]]` | 같은 곳 | 바로 `BLOCK`(helpKind `CHILD_SAFETY`) → ST-05 |
| `[[FAIL]]` | 전하고 싶은 말이나 인터뷰 답변 | 첫 생성이 "글 쓰기" 단계에서 실패 → ST-03. 다시 시도하면 성공 |
| `[[OUTPUT_REVISE]]` | 전하고 싶은 말이나 인터뷰 답변 | 첫 생성 결과에 위기 신호 → 실패(ST-03). 다시 시도하면 성공 |
| `[[PAGE_FAIL]]` | 전하고 싶은 말이나 인터뷰 답변 | 첫 생성 결과에서 **5쪽 그림만** 실패 (`imageStatus: FAILED`, `imageUrl: null`). 그림만 다시(5쪽)로 고칠 수 있다 |
| `[[AI_DOWN]]` | 글을 받는 곳 어디든 | 그 글을 처음 보낸 뒤 **다음 AI 호출 한 번**이 503 `AI_UNAVAILABLE`. 같은 글로 다시 보내면 성공 |

`[[AI_DOWN]]` 예시

- 전하고 싶은 말에 넣으면: `PUT /message`는 성공 → 다음 `POST /interview/start`가 한 번 503 → 다시 누르면 성공.
- 인터뷰 답변에 넣으면: 그 답변이 503 → 같은 답변으로 다시 보내면 성공.
- 생성 진행(`GET /generation`)에는 영향이 없다.

## 설정 (`application-local.yml`)

```yaml
firstory:
  ai:
    fake:
      interview-delay: 2s     # 인터뷰 시작·답변 응답 시간
      safety-delay: 1s        # 글 검사 응답 시간
      stage-duration: 40s     # 생성 한 단계 시간 (4단계). 0이면 진행 확인 때마다 한 단계씩(테스트용)
      turns: 0                # 0이면 동화마다 질문 1~5개. 1~4면 질문 수가 turns+1개로 고정
```

빨리 확인하고 싶을 때는 띄울 때 환경 변수로 덮어쓴다.

```bash
FIRSTORY_AI_FAKE_STAGE_DURATION=5s ./gradlew bootRun --args='--spring.profiles.active=local'   # 생성 약 20초
```

```powershell
$env:FIRSTORY_AI_FAKE_STAGE_DURATION="5s"; .\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

질문 수를 고정하려면 `turns: 2`(항상 3개). 서버가 진행 상태를 5초마다 확인하므로 GEN-01의 단계 변화는 최대 5초 늦게 보일 수 있다(진짜도 같다).

## 가짜 그림

가짜 AI의 그림은 진짜와 같은 1920×1088 PNG로, 쪽마다 다른 바탕색에 밝은 원이 하나 있다. 서버의 `./data/storage`에 쌓인다. 지우면 이미 만든 동화의 그림은 404 `IMAGE_NOT_FOUND`가 된다.
