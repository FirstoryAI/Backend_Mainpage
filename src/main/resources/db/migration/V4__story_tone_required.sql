-- V4: 민감 주제(SENSITIVE)가 감지된 동화는 생성 전에 분위기(tone)를 골라야 한다.
-- SENSITIVE는 safety_flags에 기록하지 않으므로, 동화 입력에 표시만 남긴다.
ALTER TABLE story_inputs ADD COLUMN tone_required boolean NOT NULL DEFAULT false;
