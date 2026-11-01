-- V9: AI 인터뷰 질문 상한을 5개에서 15개로 늘린다. V1의 이름 없는 CHECK는 PostgreSQL 기본 이름(interview_turns_turn_no_check)으로 만들어졌다.
ALTER TABLE interview_turns DROP CONSTRAINT interview_turns_turn_no_check;
ALTER TABLE interview_turns ADD CONSTRAINT interview_turns_turn_no_check CHECK (turn_no BETWEEN 1 AND 15);
