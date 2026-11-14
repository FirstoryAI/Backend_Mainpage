-- 동화책 앞표지·뒤표지 (쪽과 같은 1920×1088, Backend#61, AI_worker#88)
-- 앞표지는 제목 포함, 뒤표지는 글자 없음. 없으면(이전 버전, 표지 실패) NULL
ALTER TABLE story_versions
    ADD COLUMN front_cover_key varchar(255),
    ADD COLUMN back_cover_key  varchar(255);
