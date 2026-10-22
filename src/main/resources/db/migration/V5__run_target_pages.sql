-- V5: "그림만 다시" 할 때 부모가 고른 쪽 번호를 실행 기록에 남긴다. 비어 있으면 전체.
ALTER TABLE generation_runs ADD COLUMN target_pages jsonb NOT NULL DEFAULT '[]';

-- 생성 진행 확인 스케줄러가 실행 중인 작업을 빠르게 찾도록
CREATE INDEX ix_generation_runs_running ON generation_runs (started_at) WHERE status = 'RUNNING';
