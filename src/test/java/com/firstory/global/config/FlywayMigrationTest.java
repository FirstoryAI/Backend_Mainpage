package com.firstory.global.config;

import com.firstory.support.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 컨텍스트가 뜬 것 자체가 ddl-auto=validate 통과를 뜻한다. 여기서는 적용 순서와 결과를 확인한다.
 */
class FlywayMigrationTest extends IntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void V1부터_V18까지_순서로_모두_성공한다() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
                String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
                "17", "18", "19", "20", "21");
        assertThat(jdbc.queryForObject("SELECT bool_and(success) FROM flyway_schema_history", Boolean.class))
                .isTrue();
    }

    @Test
    void V3_컬럼이_있다() {
        assertThat(columns("children")).contains("interest_codes", "custom_interests", "custom_trait")
                .doesNotContain("interests");
        assertThat(columns("story_inputs")).contains("situation_custom_category", "moment_reactions",
                        "moment_custom_reaction", "emotions", "parent_response", "parent_concern", "final_context")
                .doesNotContain("moment_reaction", "emotion");
        assertThat(columns("safety_flags")).contains("level");
    }

    @Test
    void V4_tone_required는_기본값_false() {
        assertThat(columnInfo("story_inputs", "tone_required", "column_default")).isEqualTo("false");
    }

    @Test
    void V5_target_pages와_실행_중_인덱스가_있다() {
        assertThat(columns("generation_runs")).contains("target_pages");
        assertThat(indexCount("ix_generation_runs_running")).isOne();
    }

    @Test
    void V6_text_position은_기본값_BOTTOM() {
        assertThat(columnInfo("story_pages", "text_position", "column_default")).startsWith("'BOTTOM'");
    }

    @Test
    void V7_가이드에_줄거리_컬럼이_있다() {
        assertThat(columns("reading_guides")).contains("synopsis");
    }

    @Test
    void V8_버전에_표지_컬럼이_있다() {
        assertThat(columns("story_versions")).contains("cover_key");
    }

    @Test
    void V14_버전에_앞뒤표지_칸이_있다() {
        assertThat(jdbc.queryForList("""
                SELECT column_name || ':' || data_type || ':' || is_nullable FROM information_schema.columns
                WHERE table_name = 'story_versions' AND column_name IN ('front_cover_key', 'back_cover_key')
                ORDER BY column_name""", String.class))
                .containsExactly("back_cover_key:character varying:YES", "front_cover_key:character varying:YES");
    }

    @Test
    void V9_인터뷰_차례는_1에서_15() {
        assertThat(jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'interview_turns_turn_no_check'""", String.class))
                .contains(">= 1").contains("<= 15");
    }

    @Test
    void V10_입력에_상황_정리_컬럼이_있다() {
        assertThat(columns("story_inputs")).contains("situation_summary");
    }

    @Test
    void V17_확정과_읽기_완료_피드백_테이블이_있고_하나씩만_저장된다() {
        assertThat(columns("story_feedbacks")).contains("story_id", "version_id", "situation_reflection",
                "story_satisfaction", "reading_intent", "comment");
        assertThat(columns("reading_feedbacks")).contains("session_id", "story_id", "child_interest",
                "conversation_flow", "conversation_helpfulness", "comment");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE constraint_type = 'UNIQUE' AND table_name IN ('story_feedbacks', 'reading_feedbacks')""",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void V16_쪽에_의성어_자리_컬럼이_있고_비어_있어도_된다() {
        assertThat(columns("story_pages")).contains("sound_word");
        assertThat(columnInfo("story_pages", "sound_word", "is_nullable")).isEqualTo("YES");
        assertThat(columnInfo("story_pages", "sound_word", "data_type")).isEqualTo("jsonb");
    }

    @Test
    void V11_쪽에_글_덩어리_배치_컬럼이_있고_비어_있어도_된다() {
        assertThat(columns("story_pages")).contains("text_blocks");
        assertThat(columnInfo("story_pages", "text_blocks", "is_nullable")).isEqualTo("YES");
        assertThat(columnInfo("story_pages", "text_blocks", "data_type")).isEqualTo("jsonb");
    }

    @Test
    void V12_휴대폰_컬럼과_문자_기록이_없고_카카오_회원번호와_이메일_인증_시각이_있다() {
        assertThat(columns("users")).contains("kakao_id")
                .doesNotContain("phone_enc", "phone_hash", "phone_verified_at");
        assertThat(columns("email_send_logs")).contains("email_hash", "purpose");
        assertThat(columns("sms_send_logs")).isEmpty();
        assertThat(columnInfo("users", "password_hash", "is_nullable")).isEqualTo("YES");
        assertThat(indexCount("ux_users_kakao_id_active")).isOne();
    }

    @Test
    void V13_대화_질문에_장면_설명_컬럼이_있다() {
        assertThat(columns("guide_prompts")).contains("scene_note");
    }

    @Test
    void V15_동의_기록과_이메일_인증_시각이_없고_알림은_실행_종류_상태만_남는다() {
        assertThat(columns("user_consents")).isEmpty();
        assertThat(columns("users")).contains("email_notify", "onboarding_seen_at").doesNotContain("email_verified_at");
        assertThat(columnInfo("users", "email_notify", "data_type || ':' || is_nullable || ':' || column_default"))
                .isEqualTo("boolean:NO:false");
        assertThat(columns("notifications")).containsExactlyInAnyOrder("id", "generation_run_id", "type", "status",
                "fail_reason", "created_at", "updated_at");
    }

    @Test
    void V15_최신_EMAIL_NOTIFY_동의가_살아_있는_회원만_email_notify가_true() {
        String schema = "v15_check";
        Flyway.configure().dataSource(dataSource).schemas(schema).target("14").load().migrate();
        try {
            long on = insertUser(schema, "on@example.com");
            long withdrawn = insertUser(schema, "withdrawn@example.com");
            long declined = insertUser(schema, "declined@example.com");
            long reagreed = insertUser(schema, "reagreed@example.com");
            long none = insertUser(schema, "none@example.com");
            consent(schema, on, "EMAIL_NOTIFY", true, false);
            consent(schema, on, "TERMS", true, false);
            consent(schema, withdrawn, "EMAIL_NOTIFY", true, true);
            consent(schema, declined, "EMAIL_NOTIFY", false, false);
            consent(schema, declined, "TERMS", true, false);
            consent(schema, reagreed, "EMAIL_NOTIFY", true, true);
            consent(schema, reagreed, "EMAIL_NOTIFY", true, false);
            consent(schema, none, "TERMS", true, false);

            Flyway.configure().dataSource(dataSource).schemas(schema).load().migrate();

            assertThat(jdbc.queryForList("SELECT email FROM " + schema + ".users WHERE email_notify ORDER BY email",
                    String.class)).containsExactly("on@example.com", "reagreed@example.com");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".users", Integer.class)).isEqualTo(5);
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private long insertUser(String schema, String email) {
        return jdbc.queryForObject("INSERT INTO " + schema + ".users (email, password_hash, email_verified_at, "
                + "created_at, updated_at) VALUES (?, 'x', now(), now(), now()) RETURNING id", Long.class, email);
    }

    private void consent(String schema, long userId, String type, boolean agreed, boolean withdrawn) {
        jdbc.update("INSERT INTO " + schema + ".user_consents (user_id, consent_type, agreed, terms_version, agreed_at, "
                + "withdrawn_at, created_at, updated_at) VALUES (?, ?, ?, '2026-10-01', now(), "
                + (withdrawn ? "now()" : "NULL") + ", now(), now())", userId, type, agreed);
    }

    @Test
    void V18_이용권_초대_코드_사전_연락_테이블과_가입_경로가_있다() {
        assertThat(columns("story_credits")).contains("user_id", "source", "amount", "expires_at", "invite_code_id");
        assertThat(columns("story_credit_uses")).contains("credit_id", "story_id");
        assertThat(columns("invite_codes")).contains("code", "max_uses", "used_count", "closes_at", "credit_amount",
                "credit_expires_at");
        assertThat(columns("pre_contacts")).contains("email", "credit_amount", "credit_expires_at", "granted_user_id");
        assertThat(columns("users")).contains("signup_source");
    }

    @Test
    void V19_사전_연락에_받을_수_있는_마감이_있다() {
        assertThat(columns("pre_contacts")).contains("closes_at", "credit_expires_at");
    }

    @Test
    void V20_회원에_정식_출시_알림_신청_시각이_있다() {
        assertThat(columns("users")).contains("subscription_alert_at");
    }

    @Test
    void V21_쪽에_음성_key와_상태가_있고_비어_있어도_된다() {
        assertThat(columns("story_pages")).contains("audio_key", "audio_status");
        assertThat(columnInfo("story_pages", "audio_key", "is_nullable")).isEqualTo("YES");
        assertThat(columnInfo("story_pages", "audio_status", "is_nullable")).isEqualTo("YES");
    }

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String.class, table);
    }

    /**
     * 컬럼 한 개의 information_schema 값. expression은 그 행에서 읽을 식이다 (예: "is_nullable").
     */
    private String columnInfo(String table, String column, String expression) {
        return jdbc.queryForObject("SELECT " + expression
                        + " FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
                String.class, table, column);
    }

    private int indexCount(String indexName) {
        return jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname = ?", Integer.class, indexName);
    }
}
