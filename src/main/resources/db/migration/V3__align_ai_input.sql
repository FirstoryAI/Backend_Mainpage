-- V3: AI 파이프라인 입력 형식(StoryInputContext)에 맞추기 + 위기 신호 3단계
-- 값 이름은 AI와 같은 단어를 대문자로 저장한다 (AI로 보낼 때 소문자로 바꾼다).

-- ───────── 아이 프로필 ─────────
-- 좋아하는 것: 목록에서 고른 값과 직접 입력을 나눠 저장 (합쳐서 최대 5개는 앱에서 검사)
ALTER TABLE children RENAME COLUMN interests TO custom_interests;
ALTER TABLE children
    ADD COLUMN interest_codes jsonb       NOT NULL DEFAULT '[]',
    ADD COLUMN custom_trait   varchar(20);

-- 값 이름을 AI와 맞춤
UPDATE children     SET gender     = 'UNSPECIFIED' WHERE gender     = 'UNDISCLOSED';
UPDATE child_traits SET trait_code = 'CAUTIOUS'    WHERE trait_code = 'SLOW_TO_WARM';

-- ───────── 만들기 입력 ─────────
ALTER TABLE story_inputs
    DROP COLUMN moment_reaction,
    DROP COLUMN emotion,
    ADD COLUMN situation_custom_category  varchar(30),
    ADD COLUMN moment_reactions           jsonb NOT NULL DEFAULT '[]',
    ADD COLUMN moment_custom_reaction     varchar(30),
    ADD COLUMN emotions                   jsonb NOT NULL DEFAULT '[]',
    ADD COLUMN parent_response            text,
    ADD COLUMN parent_concern             text,
    ADD COLUMN final_context              jsonb;   -- 인터뷰가 끝난 뒤 AI가 확정한 context 전체

-- ───────── 쪽수: DB에서 상한을 빼고 앱 설정(firstory.story.page-count)으로 검사 ─────────
ALTER TABLE story_pages   DROP CONSTRAINT story_pages_page_no_check;
ALTER TABLE story_pages   ADD  CONSTRAINT story_pages_page_no_check   CHECK (page_no >= 1);
ALTER TABLE guide_prompts DROP CONSTRAINT guide_prompts_after_page_check;
ALTER TABLE guide_prompts ADD  CONSTRAINT guide_prompts_after_page_check CHECK (after_page >= 1);

-- ───────── 위기 신호 3단계 ─────────
-- level: REVISE(수정 요청) / CRISIS(즉시 중단). OK, SENSITIVE는 기록하지 않는다.
ALTER TABLE safety_flags ADD COLUMN level varchar(20) NOT NULL DEFAULT 'REVISE';
ALTER TABLE safety_flags ALTER COLUMN level DROP DEFAULT;
-- 같은 동화에서 단계별로 몇 번째인지 셀 수 있도록 occurrence_no의 UNIQUE를 단계 포함으로 바꿈
ALTER TABLE safety_flags DROP CONSTRAINT safety_flags_story_id_occurrence_no_key;
ALTER TABLE safety_flags ADD  CONSTRAINT safety_flags_story_level_occurrence_key UNIQUE (story_id, level, occurrence_no);
