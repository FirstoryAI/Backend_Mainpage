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
    void V1부터_V15까지_순서로_모두_성공한다() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
                String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15");
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
        assertThat(jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_name = 'story_inputs' AND column_name = 'tone_required'""", String.class))
                .isEqualTo("false");
    }

    @Test
    void V5_target_pages와_실행_중_인덱스가_있다() {
        assertThat(columns("generation_runs")).contains("target_pages");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname = 'ix_generation_runs_running'",
                Integer.class)).isOne();
    }

    @Test
    void V6_text_position은_기본값_BOTTOM() {
        assertThat(jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_name = 'story_pages' AND column_name = 'text_position'""", String.class))
                .startsWith("'BOTTOM'");
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
    void V11_쪽에_글_덩어리_배치_컬럼이_있고_비어_있어도_된다() {
        assertThat(columns("story_pages")).contains("text_blocks");
        assertThat(jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'story_pages' AND column_name = 'text_blocks'""", String.class)).isEqualTo("YES");
        assertThat(jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'story_pages' AND column_name = 'text_blocks'""", String.class)).isEqualTo("jsonb");
    }

    @Test
    void V12_휴대폰_컬럼과_문자_기록이_없고_카카오_회원번호와_이메일_인증_시각이_있다() {
        assertThat(columns("users")).contains("kakao_id")
                .doesNotContain("phone_enc", "phone_hash", "phone_verified_at");
        assertThat(columns("email_send_logs")).contains("email_hash", "purpose");
        assertThat(columns("sms_send_logs")).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'users' AND column_name = 'password_hash'""", String.class)).isEqualTo("YES");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname = 'ux_users_kakao_id_active'",
                Integer.class)).isOne();
    }

    @Test
    void V13_대화_질문에_장면_설명_컬럼이_있다() {
        assertThat(columns("guide_prompts")).contains("scene_note");
    }

    @Test
    void V15_동의_기록과_이메일_인증_시각이_없고_알림은_실행_종류_상태만_남는다() {
        assertThat(columns("user_consents")).isEmpty();
        assertThat(columns("users")).contains("email_notify", "onboarding_seen_at").doesNotContain("email_verified_at");
        assertThat(jdbc.queryForObject("""
                SELECT data_type || ':' || is_nullable || ':' || column_default FROM information_schema.columns
                WHERE table_name = 'users' AND column_name = 'email_notify'""", String.class))
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

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String.class, table);
    }
}
