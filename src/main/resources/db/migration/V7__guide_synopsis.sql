-- V7: 동화 줄거리. AI 가이드가 만든 해요체 2~3문장으로, 서재 상세의 "동화 줄거리"에 보여 준다. 이전 가이드는 null.
ALTER TABLE reading_guides ADD COLUMN synopsis text;
