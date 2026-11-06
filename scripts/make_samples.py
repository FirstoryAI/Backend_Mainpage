#!/usr/bin/env python3
"""홈 샘플 카드의 동화를 미리 만든다 (Backend#45).

샘플 전용 계정(이메일 가입)으로 로그인해 카드마다 상황 → 아이 → 전하고 싶은 말 → 인터뷰 → 만들기 → 확정까지 돌린다.
인터뷰는 답변 카드로 답한다: 마무리 질문이면 "아니요, 동화 만들러 갈게요", 아니면 첫 번째 카드, 카드가 없으면 건너뛰기.
분위기를 고르라고 하면 WARM. 그림만 다시는 하지 않고 첫 버전을 확정한다.

끝나면 카드별 동화 id와 SAMPLE_STORIES 값을 출력하고, 카드마다 입력·인터뷰·책(글, 대화 가이드 포함)·그림을 OUT 폴더에 저장한다.
진짜 AI라 한 편에 3~4분, 비용이 든다. 카드는 하나씩 차례로 만든다.

    SAMPLE_EMAIL=... SAMPLE_PASSWORD=... python3 scripts/make_samples.py              # 8개 모두
    SAMPLE_EMAIL=... SAMPLE_PASSWORD=... python3 scripts/make_samples.py friend emotion   # 고른 카드만

환경 변수: BASE_URL(기본 https://api.firstoryofficial.com), SAMPLE_EMAIL, SAMPLE_PASSWORD,
          OUT(기본 build/samples/<시각>), GENERATION_TIMEOUT(초, 기본 900)
python3 표준 라이브러리만 쓴다.
"""

import http.cookiejar
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE_URL = os.environ.get("BASE_URL", "https://api.firstoryofficial.com").rstrip("/")
GENERATION_TIMEOUT = int(os.environ.get("GENERATION_TIMEOUT", "900"))
FINISH_CHOICE = "아니요, 동화 만들러 갈게요"

# 상황 설명은 프론트 scenarioPopupContent.js의 팝업 문장 그대로. 나이는 2026-11 기준 만 나이
CARDS = {
    "friend": {
        "situation": {"timing": "PAST", "category": "FRIEND_CONFLICT",
                      "description": "놀이 시간에 친구가 블록을 말없이 가져갔는데, 그 자리에선 아무 말도 못 했대요. "
                                     "집에 와서 속상해하는데, 뭐라고 답할지 모르겠어요.",
                      "reactions": ["SILENT"], "emotions": ["SAD"]},
        "child": {"birthYm": "2021-05", "gender": "MALE", "traits": ["SHY"], "interestCodes": ["VEHICLE"]},
        "message": "싫을 땐 \"싫어, 돌려줘\"라고 말해도 괜찮아",
    },
    "friendLeftOut": {
        "situation": {"timing": "PAST", "category": "FRIEND_CONFLICT",
                      "description": "아이가 집에 와서 친구가 자기랑 놀기 싫다고 했다며 속상해 했어요. "
                                     "평소 자기 마음을 잘 표현하지 못하는 아이라, 어떻게 이야기를 꺼내야 할지 고민이에요.",
                      "reactions": ["CRIED"], "emotions": ["SAD"]},
        "child": {"birthYm": "2021-05", "gender": "FEMALE", "traits": ["CAUTIOUS", "SHY"],
                  "interestCodes": ["DRAWING"]},
        "message": "네 마음을 말해 주면 언제든 들어 줄게",
    },
    "emotion": {
        "situation": {"timing": "PAST", "category": "FAMILY_CHANGE",
                      "description": "동생이 태어난 뒤로 아기 말투를 쓰고, 이유 없이 울거나 동생을 밀기도 해요. "
                                     "첫째 마음도 챙기고 싶은데, 어디서부터 이야기해야 할지 모르겠어요.",
                      "reactions": ["ANGRY_OUTBURST", "CLINGY"], "emotions": ["ANGRY", "SAD"]},
        "child": {"birthYm": "2022-05", "gender": "MALE", "traits": ["SENSITIVE"], "interestCodes": ["DINOSAUR"]},
        "message": "동생이 와도 엄마 아빠는 너를 똑같이 사랑해",
    },
    "familyApart": {
        "situation": {"timing": "PAST", "category": "FAMILY_CHANGE",
                      "description": "아빠와 떨어져 지내게 된 뒤로, 잠들기 전마다 보고 싶다며 울어요. "
                                     "\"나 때문에 아빠가 안 와?\"라고 묻는데, 어떻게 답해야 할지 막막해요.",
                      "reactions": ["CRIED", "ASKED_QUESTIONS"], "emotions": ["SAD", "CONFUSED"]},
        "child": {"birthYm": "2021-05", "gender": "FEMALE", "traits": ["SENSITIVE"], "interestCodes": ["ANIMAL"]},
        "message": "아빠가 따로 지내는 건 너 때문이 아니야",
    },
    "kindergarten": {
        "situation": {"timing": "PAST", "category": "NEW_ENVIRONMENT",
                      "description": "유치원에 다닌 지 한 달이 넘었는데, 아직도 아침마다 현관 앞에서 한참을 울어요. "
                                     "하원 후에도 짜증이 늘어서 어떻게 마음을 풀어 줘야 할지 고민이에요.",
                      "reactions": ["CRIED", "CLINGY"], "emotions": ["SCARED"]},
        "child": {"birthYm": "2021-05", "gender": "MALE", "traits": ["CAUTIOUS"], "interestCodes": ["SPACE"]},
        "message": "낯선 곳이 무서운 건 당연해, 천천히 익숙해져도 돼",
    },
    "kindergartenScared": {
        "situation": {"timing": "PAST", "category": "NEW_ENVIRONMENT",
                      "description": "헤어질 때마다 다리를 붙잡고 \"엄마 진짜 데리러 와?\"라고 몇 번이고 물어요. "
                                     "헤어진 뒤에도 한참 창문만 본다는데, 어떻게 안심시켜야 할지 모르겠어요.",
                      "reactions": ["CLINGY", "ASKED_QUESTIONS"], "emotions": ["SCARED"]},
        "child": {"birthYm": "2020-05", "gender": "FEMALE", "traits": ["SENSITIVE"], "interestCodes": ["ANIMAL"]},
        "message": "엄마는 꼭 데리러 와",
    },
    "tantrum": {
        "situation": {"timing": "PAST", "category": "OTHER", "customCategory": "갑자기 터지는 짜증",
                      "description": "웃으며 놀다가도 컵 색깔 같은 작은 일로 소리를 지르며 울어요. "
                                     "왜 그러냐고 물으면 \"몰라, 그냥 짜증 나\"라고만 해서 답답해요.",
                      "reactions": ["ANGRY_OUTBURST", "CRIED"], "emotions": ["ANGRY", "CONFUSED"]},
        "child": {"birthYm": "2020-05", "gender": "MALE", "traits": ["ACTIVE", "STUBBORN"],
                  "interestCodes": ["HERO"]},
        "message": "짜증 나는 마음도 괜찮아, 말로 알려 주면 같이 풀어 보자",
    },
    "clothesTeeth": {
        "situation": {"timing": "PAST", "category": "RULES",
                      "description": "아침저녁으로 옷을 입히고 양치를 시키려 하면 \"싫어!\" 하며 도망 다녀요. "
                                     "혼을 내면 드러누워 더 크게 울어서, 어떻게 해야 덜 부딪힐지 고민이에요.",
                      "reactions": ["ANGRY_OUTBURST", "AVOIDED"], "emotions": ["ANGRY"]},
        "child": {"birthYm": "2020-05", "gender": "FEMALE", "traits": ["STUBBORN"], "interestCodes": ["DINOSAUR"]},
        "message": "옷 입고 이 닦는 건 너를 지켜 주는 일이야, 같이 재미있게 해 보자",
    },
}


class Failed(Exception):
    pass


class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def _xsrf(self):
        return next((c.value for c in self.jar if c.name == "XSRF-TOKEN"), "")

    def raw(self, method, path, body=None):
        headers = {"Accept": "application/json"}
        data = None
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode()
            headers["Content-Type"] = "application/json"
        if method != "GET":
            headers["X-XSRF-TOKEN"] = self._xsrf()
        request = urllib.request.Request(BASE_URL + path, data=data, method=method, headers=headers)
        try:
            with self.opener.open(request, timeout=60) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as e:
            return e.code, e.read()

    def call(self, method, path, body=None, expect=(200,)):
        # AI가 잠깐 바쁘거나 응답하지 않으면 같은 요청을 다시 보낸다 (아무것도 저장되지 않는다)
        for attempt in range(4):
            status, payload = self.raw(method, path, body)
            text = payload.decode(errors="replace")
            if status in (503, 409) and ('"AI_UNAVAILABLE"' in text or '"AI_BUSY"' in text) and attempt < 3:
                time.sleep(5)
                continue
            if status not in expect:
                raise Failed(f"{method} {path} → HTTP {status} {text[:500]}")
            return json.loads(text) if text.strip() else None


def action(response):
    safety = (response or {}).get("safety") or {}
    return safety.get("action")


def step(client, method, path, body, log):
    response = client.call(method, path, body)
    log.append({"request": f"{method} {path}", "body": body, "response": response})
    if response and response.get("status") == "BLOCKED":
        raise Failed(f"{path}에서 차단됐어요: {response.get('safety')}")
    if action(response) == "ASK_REVISE":
        raise Failed(f"{path}에서 다시 쓰기 안내가 나왔어요: {response.get('safety')}")
    return response


def pick_answer(response):
    choices = response.get("choices") or []
    if action(response) == "ASK_REVISE" or not choices:
        return {"skip": True}
    if FINISH_CHOICE in choices:
        return {"answer": FINISH_CHOICE}
    return {"answer": choices[0]}


def make(client, card_id, card, out):
    log = []
    story_id = client.call("POST", "/api/stories", expect=(201,))["storyId"]
    story = f"/api/stories/{story_id}"
    print(f"  storyId={story_id}", flush=True)

    tone_needed = False
    for method, path, body in (("PUT", f"{story}/situation", card["situation"]),
                               ("PUT", f"{story}/child",
                                {"childId": None, "saveForNextTime": False, "profile": card["child"]}),
                               ("PUT", f"{story}/message", {"message": card["message"]})):
        response = step(client, method, path, body, log)
        tone_needed = tone_needed or action(response) == "CHOOSE_TONE" or response.get("toneRequired")

    interview = []
    response = client.call("POST", f"{story}/interview/start")
    while response.get("type") == "QUESTION":
        if len(interview) >= 15:
            raise Failed("질문 15개 뒤에도 인터뷰가 끝나지 않았어요")
        answer = pick_answer(response)
        interview.append({"turn": response.get("turn"), "question": response.get("question"),
                          "choices": response.get("choices"), "sent": answer})
        response = client.call("POST", f"{story}/interview/answer", answer)
        if response.get("status") == "BLOCKED":
            raise Failed(f"인터뷰 답변에서 차단됐어요: {response.get('safety')}")
        tone_needed = tone_needed or response.get("toneRequired")
    print(f"  인터뷰 질문 {len(interview)}개", flush=True)

    if tone_needed:
        client.call("PUT", f"{story}/tone", {"tone": "WARM"})
    draft = client.call("GET", f"{story}/draft")

    client.call("POST", f"{story}/confirm", expect=(202,))
    began = time.time()
    while True:
        generation = client.call("GET", f"{story}/generation")
        if generation.get("status") == "REVIEWING":
            break
        if generation.get("status") == "FAILED":
            raise Failed(f"생성이 실패했어요: {generation}")
        if time.time() - began > GENERATION_TIMEOUT:
            raise Failed(f"{GENERATION_TIMEOUT}초 안에 끝나지 않았어요: {generation}")
        time.sleep(5)
    seconds = round(time.time() - began)
    print(f"  만들기 {seconds}초", flush=True)

    client.call("POST", f"{story}/approve")
    book = client.call("GET", f"{story}/book")
    guide = client.call("GET", f"{story}/guide")
    detail = client.call("GET", story)

    folder = out / card_id
    folder.mkdir(parents=True, exist_ok=True)
    images = [("cover", book.get("coverUrl"))] + [
        (f"page-{page['pageNo']:02d}", page.get("imageUrl")) for page in book["pages"]]
    for name, url in images:
        if not url:
            continue
        status, payload = client.raw("GET", url)
        if status == 200:
            ext = "webp" if payload[8:12] == b"WEBP" else "png"
            (folder / f"{name}.{ext}").write_bytes(payload)
    (folder / "story.json").write_text(json.dumps({
        "cardId": card_id, "storyId": story_id, "generationSeconds": seconds, "inputs": card,
        "steps": log, "interview": interview, "draft": draft, "detail": detail, "book": book, "guide": guide,
    }, ensure_ascii=False, indent=2))
    return story_id


def main():
    email, password = os.environ.get("SAMPLE_EMAIL"), os.environ.get("SAMPLE_PASSWORD")
    if not email or not password:
        sys.exit("SAMPLE_EMAIL과 SAMPLE_PASSWORD를 넣어 주세요 (샘플 전용 이메일 계정)")
    card_ids = sys.argv[1:] or list(CARDS)
    unknown = [card_id for card_id in card_ids if card_id not in CARDS]
    if unknown:
        sys.exit(f"없는 카드예요: {unknown}. 고를 수 있는 카드: {list(CARDS)}")
    out = Path(os.environ.get("OUT", f"build/samples/{time.strftime('%Y%m%d-%H%M%S')}"))
    out.mkdir(parents=True, exist_ok=True)

    client = Client()
    client.call("GET", "/api/auth/csrf", expect=(200, 204))
    client.call("POST", "/api/auth/login", {"email": email, "password": password, "rememberMe": False})
    print(f"{BASE_URL} 로그인 → 카드 {len(card_ids)}개, 결과 {out}", flush=True)

    made, failed = {}, {}
    for card_id in card_ids:
        print(f"[{card_id}]", flush=True)
        try:
            made[card_id] = make(client, card_id, CARDS[card_id], out)
        except Failed as e:
            failed[card_id] = str(e)
            print(f"  실패: {e}", flush=True)

    print()
    for card_id, story_id in made.items():
        print(f"{card_id}: 동화 {story_id}")
    for card_id, reason in failed.items():
        print(f"{card_id}: 실패 ({reason})")
    if made:
        print("\nSAMPLE_STORIES=" + ",".join(f"{card_id}:{story_id}" for card_id, story_id in made.items()))
    (out / "result.json").write_text(json.dumps({"made": made, "failed": failed}, ensure_ascii=False, indent=2))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
