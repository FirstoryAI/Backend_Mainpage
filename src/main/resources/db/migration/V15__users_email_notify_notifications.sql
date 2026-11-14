-- 새 ERD(11/9 수정) 기준. Backend#54
-- 동의 기록(user_consents)을 지우고 알림 수신 동의만 users.email_notify로 둔다. 필수 동의(보호자·약관·개인정보)는 가입 = 동의, 시각은 users.created_at
-- 이메일 인증 시각(users.email_verified_at)과 알림의 쓰지 않는 칸을 지운다. 보낸 시각은 notifications.updated_at

-- ───────── 회원: 알림 수신 동의 ─────────
ALTER TABLE users ADD COLUMN email_notify boolean NOT NULL DEFAULT false;

-- 회원마다 가장 최근 EMAIL_NOTIFY 기록이 동의했고 철회하지 않았으면 true
UPDATE users u SET email_notify = true
FROM (SELECT DISTINCT ON (user_id) user_id, agreed, withdrawn_at
      FROM user_consents
      WHERE consent_type = 'EMAIL_NOTIFY'
      ORDER BY user_id, id DESC) latest
WHERE latest.user_id = u.id AND latest.agreed AND latest.withdrawn_at IS NULL;

DROP TABLE user_consents;

ALTER TABLE users DROP COLUMN email_verified_at;

-- ───────── 알림: 실행·종류·상태만 ─────────
ALTER TABLE notifications
    DROP COLUMN user_id,
    DROP COLUMN story_id,
    DROP COLUMN channel,
    DROP COLUMN template_code,
    DROP COLUMN provider_msg_id,
    DROP COLUMN sent_at;
