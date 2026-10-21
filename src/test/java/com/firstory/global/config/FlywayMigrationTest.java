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
    void V1_V2_V3_순서로_모두_성공한다() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
                String.class)).containsExactly("1", "2", "3");
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

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?", String.class, table);
    }
}
