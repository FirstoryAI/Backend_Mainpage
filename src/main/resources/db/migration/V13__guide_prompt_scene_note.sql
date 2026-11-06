-- V13: 대화 질문마다 부모용 장면 설명 (미리보기 가이드 👆 칸). 옛 동화는 null.
ALTER TABLE guide_prompts ADD COLUMN scene_note text;
