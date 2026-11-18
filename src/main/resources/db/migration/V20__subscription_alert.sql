-- 무료 동화를 다 쓴 회원이 "정식 출시 알림 받기"를 누른 시각. NULL이면 신청하지 않음.
-- 정식 출시(구독)가 열리면 이 시각이 있는 회원에게 이메일로 알린다 (광고성 정보 수신 신청 기록).
ALTER TABLE users ADD COLUMN subscription_alert_at timestamptz;
