-- V16: 쪽의 의성어·의태어를 그림 위 어디에 띄울지 AI가 정한 자리(sound_word, {text, x, y, color}). 옛 쪽·별표 말 없는 쪽은 null.
ALTER TABLE story_pages ADD COLUMN sound_word jsonb;
