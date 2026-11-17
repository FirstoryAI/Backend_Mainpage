#!/usr/bin/env python3
"""사전 연락(pre_contacts) 이메일에 베타 안내 메일을 한 번 보낸다. EC2의 deploy/ec2 폴더에서 실행한다.

  python3 pre-contact-mail/send.py preview            # 보내지 않고 미리보기 HTML만 만든다
  python3 pre-contact-mail/send.py test 내메일@x.com   # 두 종류를 내 메일로만 한 통씩
  python3 pre-contact-mail/send.py send               # 실제로 보낸다 (보내기 전에 묻는다)

- 받는 사람: pre_contacts에서 받을 수 있는 마감(closes_at)이 지나지 않은 이메일.
  아직 회원이 아니면 가입 안내(signup.html), 회원이면 로그인 안내(login.html).
- 보내는 계정: .env의 MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM (서버 인증번호 메일과 같은 팀 Gmail).
  그림 주소는 https://{API_DOMAIN}/mail, 버튼은 {APP_URL}/auth.
- 보낸 주소는 sent.log에 이메일의 SHA-256만 남겨 다시 실행해도 두 번 보내지 않는다.
- 화면과 로그에는 이메일을 가려서(p*****@gmail.com) 보여 준다. 비밀번호는 출력하지 않는다.
"""
import hashlib
import os
import smtplib
import subprocess
import sys
import time
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.utils import formataddr, make_msgid

HERE = os.path.dirname(os.path.abspath(__file__))
COMPOSE_DIR = os.path.dirname(HERE)
SENT_LOG = os.path.join(HERE, "sent.log")
SUBJECT = "[FIRSTORY] 사전 등록자 전용 무료 동화 2편 선물이 도착했어요!"
CONTACT = "문의 firstory.official@gmail.com\n인스타그램 @firstory.official"

TEXT = {
    "signup": """아이에게 전하고 싶은 마음, 동화로 시작해 보세요

사전에 신청해 주신 FIRSTORY 서비스가 오픈했어요! 기다려 주셔서 감사해요.

이 메일을 받으신 이메일 주소로 FIRSTORY에 가입해 주세요. 해당 이메일을 통해 바로 맞춤 동화를 2편 제작할 수 있는 깜짝 선물을 드려요.

가입하고 만들기: {app}/auth

무료 동화 2편 · 2026.11.22까지 가입 시

꼭 해당 메일로 회원가입해 주세요.
(카카오로 회원가입 시 카카오 계정 이메일과 해당 메일이 일치해야 해요)
11월 22일까지 가입하시면 무료 동화 2편을 12월 31일까지 만들 수 있어요. 그 이후에 가입하시면 무료 제작은 1편이니 기간 내에 가입하시는 걸 권장드려요.
이 메일은 보내기 전용이라 답장을 받지 않아요.
""",
    "login": """아이에게 전하고 싶은 마음, 동화로 시작해 보세요

사전 신청해 주셔서 감사해요. 11월 22일까지 로그인하시면 지금 쓰고 계신 계정에 무료 동화 2편이 들어가요. 12월 31일까지 만들 수 있어요.

로그인하고 만들기: {app}/auth

무료 동화 2편 · 2026.11.22까지 로그인 시

신청하지 않으셨다면 이 메일은 지워도 괜찮아요.
이 메일은 보내기 전용이라 답장을 받지 않아요.
""",
}

RECIPIENTS_SQL = """
SELECT p.email, CASE WHEN u.id IS NULL THEN 'signup' ELSE 'login' END
FROM pre_contacts p
LEFT JOIN users u ON u.email = p.email AND u.deleted_at IS NULL
WHERE p.closes_at IS NULL OR p.closes_at > now()
ORDER BY 2, 1;
"""


def load_env():
    env = {}
    path = os.path.join(COMPOSE_DIR, ".env")
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            env[key.strip()] = value.strip().strip('"').strip("'")
    return env


def require(env, *keys):
    missing = [k for k in keys if not env.get(k)]
    if missing:
        sys.exit(".env에 값이 없어요: " + ", ".join(missing))


def mask(email):
    local, _, domain = email.partition("@")
    return (local[:1] + "*****@" + domain) if domain else "*****"


def digest(email):
    return hashlib.sha256(email.strip().lower().encode("utf-8")).hexdigest()


def render(kind, env):
    with open(os.path.join(HERE, kind + ".html"), encoding="utf-8") as f:
        html = f.read()
    app = env["APP_URL"].rstrip("/")
    html = html.replace("{{assetBase}}", "https://" + env["API_DOMAIN"] + "/mail").replace("{{appUrl}}", app)
    if "{{" in html:
        sys.exit("템플릿에 채우지 않은 칸이 있어요: " + kind)
    text = TEXT[kind].format(app=app) + "\n" + CONTACT
    return html, text


def recipients():
    out = subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "firstory", "-d", "firstory",
         "-At", "-F", "|", "-c", RECIPIENTS_SQL],
        cwd=COMPOSE_DIR, capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit("DB에서 받는 사람을 읽지 못했어요. deploy/ec2 폴더에서 실행했는지, postgres가 떠 있는지 확인해 주세요.")
    rows = []
    for line in out.stdout.splitlines():
        if "|" in line:
            email, kind = line.split("|", 1)
            rows.append((email.strip(), kind.strip()))
    return rows


def sent_digests():
    if not os.path.exists(SENT_LOG):
        return set()
    with open(SENT_LOG, encoding="utf-8") as f:
        return {line.split()[0] for line in f if line.strip()}


def connect(env):
    host = env.get("MAIL_HOST") or "smtp.gmail.com"
    port = int(env.get("MAIL_PORT") or 587)
    smtp = smtplib.SMTP(host, port, timeout=30)
    smtp.starttls()
    smtp.login(env["MAIL_USERNAME"], env["MAIL_PASSWORD"])
    return smtp


def send_one(smtp, env, to, kind, subject=SUBJECT):
    html, text = render(kind, env)
    sender = env.get("MAIL_FROM") or env["MAIL_USERNAME"]
    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"] = formataddr(("FIRSTORY", sender))
    msg["To"] = to
    msg["Message-ID"] = make_msgid(domain=sender.split("@")[-1])
    msg.attach(MIMEText(text, "plain", "utf-8"))
    msg.attach(MIMEText(html, "html", "utf-8"))
    smtp.sendmail(sender, [to], msg.as_string())


def cmd_preview(env):
    out_dir = "/tmp/firstory-mail-preview"
    os.makedirs(out_dir, exist_ok=True)
    for kind in ("signup", "login"):
        html, _ = render(kind, env)
        path = os.path.join(out_dir, kind + ".html")
        with open(path, "w", encoding="utf-8") as f:
            f.write(html)
        print("미리보기:", path)


def cmd_test(env, to):
    smtp = connect(env)
    try:
        for kind in ("signup", "login"):
            send_one(smtp, env, to, kind, "[테스트] " + SUBJECT)
            print(f"[테스트] {kind} → {mask(to)} 보냄")
            time.sleep(1)
    finally:
        smtp.quit()


def cmd_send(env):
    rows = recipients()
    done = sent_digests()
    todo = [(e, k) for e, k in rows if digest(e) not in done]
    signup = sum(1 for _, k in todo if k == "signup")
    login = len(todo) - signup
    print(f"받는 사람 {len(rows)}명 중 이미 보낸 {len(rows) - len(todo)}명을 빼고 {len(todo)}명에게 보내요.")
    print(f"  가입 안내 {signup}명, 로그인 안내 {login}명")
    for email, kind in todo:
        print(f"  - {mask(email)} ({'가입 안내' if kind == 'signup' else '로그인 안내'})")
    if not todo:
        return
    if input("보낼까요? (y/N) ").strip().lower() != "y":
        print("보내지 않았어요.")
        return
    smtp = connect(env)
    failed = 0
    try:
        for email, kind in todo:
            try:
                send_one(smtp, env, email, kind)
            except smtplib.SMTPException as e:
                failed += 1
                print(f"  실패 {mask(email)}: {type(e).__name__}")
                continue
            with open(SENT_LOG, "a", encoding="utf-8") as f:
                f.write(f"{digest(email)} {kind} {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
            print(f"  보냄 {mask(email)}")
            time.sleep(1)
    finally:
        smtp.quit()
    print(f"끝났어요. 보냄 {len(todo) - failed}통, 실패 {failed}통 (실패한 주소는 다시 실행하면 다시 보내요)")


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("preview", "test", "send"):
        sys.exit(__doc__)
    env = load_env()
    require(env, "API_DOMAIN", "APP_URL")
    if sys.argv[1] == "preview":
        cmd_preview(env)
        return
    require(env, "MAIL_USERNAME", "MAIL_PASSWORD")
    if sys.argv[1] == "test":
        if len(sys.argv) < 3 or "@" not in sys.argv[2]:
            sys.exit("시험으로 받을 이메일을 적어 주세요: python3 pre-contact-mail/send.py test 내메일@x.com")
        cmd_test(env, sys.argv[2])
    else:
        cmd_send(env)


if __name__ == "__main__":
    main()
