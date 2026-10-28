package com.firstory.global.config;

import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 컨텍스트가 뜬 것 자체가 ddl-auto=validate 통과를 뜻한다. 여기서는 적용 순서와 결과를 확인한다.
 */
class FlywayMigrationTest extends IntegrationTest {

    @Test
    void V1부터_V8까지_순서로_모두_성공한다() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
                String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
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

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?", String.class, table);
    }
}
