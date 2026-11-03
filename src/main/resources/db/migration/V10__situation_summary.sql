-- V10: AI가 인터뷰 끝에 정리한 상황. 생성 전 확인(CRT-06)의 '상황' 카드에 보여 준다. 상황을 고치면 비운다. 이전 동화는 null.
ALTER TABLE story_inputs ADD COLUMN situation_summary text;
