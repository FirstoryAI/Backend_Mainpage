-- 쪽 음성(mp3). null = 음성이 없는 쪽, 음성 기능 전에 만든 동화
ALTER TABLE story_pages
    ADD COLUMN audio_key varchar(255) NULL,
    ADD COLUMN audio_status varchar(20) NULL;
