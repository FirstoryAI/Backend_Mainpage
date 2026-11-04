-- V11: 쪽 글을 그림 위 어디에 얹을지 AI가 정한 덩어리 배치(text_blocks, [{paragraphs, x, y, color}]). 이전 쪽과 그림이 실패한 쪽은 null.
ALTER TABLE story_pages ADD COLUMN text_blocks jsonb;
