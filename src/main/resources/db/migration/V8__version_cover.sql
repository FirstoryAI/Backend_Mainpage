-- V8: 표지 그림. AI가 제목을 넣어 따로 그린 정사각형 그림의 key. 이전 버전과 표지를 못 그린 버전은 null(캐릭터 시트를 표지로 쓴다).
ALTER TABLE story_versions ADD COLUMN cover_key varchar(255);
