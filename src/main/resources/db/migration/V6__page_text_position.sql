-- V6: 쪽마다 그림 위 글을 얹을 자리. AI가 그림의 이쪽 30%를 비워 둔다. 이전 쪽은 BOTTOM.
ALTER TABLE story_pages ADD COLUMN text_position varchar(10) NOT NULL DEFAULT 'BOTTOM';  -- TOP / BOTTOM
