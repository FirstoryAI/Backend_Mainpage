package com.firstory.global.config;

import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * local 프로필처럼 springdoc을 켰을 때 문서에 요약, 성공 상태, 오류 코드가 들어가는지 확인한다.
 */
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true",
        "springdoc.swagger-ui.csrf.enabled=true"})
class OpenApiDocsTest extends IntegrationTest {

    @Autowired
    JsonMapper jsonMapper;

    @Test
    void 오류_코드와_성공_상태가_문서에_들어간다() throws Exception {
        browser().get("/v3/api-docs")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.summary").value("가입"))
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.responses['409'].description",
                        containsString("DUPLICATE_EMAIL")))
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.responses['403'].description",
                        containsString("X-XSRF-TOKEN")))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['423'].description",
                        containsString("ACCOUNT_LOCKED")))
                .andExpect(jsonPath("$.paths['/api/children/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/children/{id}'].get.responses['404'].description",
                        containsString("CHILD_NOT_FOUND")))
                .andExpect(jsonPath("$.paths['/api/me'].get.responses['401'].description",
                        containsString("UNAUTHORIZED")))
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/stories'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/stories/{id}/situation'].put.summary").value("상황 입력 (CRT-01, 02)"))
                .andExpect(jsonPath("$.paths['/api/stories/{id}/situation'].put.responses['503'].description",
                        containsString("AI_UNAVAILABLE")))
                .andExpect(jsonPath("$.paths['/api/stories/{id}/situation'].put.responses['409'].description",
                        containsString("STORY_BLOCKED")))
                .andExpect(jsonPath("$.paths['/api/stories/{id}/interview/start'].post.responses['400'].description",
                        containsString("STORY_INPUT_INCOMPLETE")))
                .andExpect(jsonPath("$.paths['/api/stories/{id}/interview/answer'].post.responses['409'].description",
                        containsString("AI_BUSY")))
                .andExpect(jsonPath("$.paths['/api/stories/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ValidationErrorResponse").exists());
    }

    /**
     * 문서 안의 모든 $ref가 components에 실제로 있는지 본다. 없으면 Swagger UI가 "Could not resolve reference"를 낸다.
     */
    @Test
    void 모든_ref가_components에_있다() throws Exception {
        String docs = browser().get("/v3/api-docs").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = jsonMapper.readTree(docs);

        Set<String> refs = new TreeSet<>();
        collectRefs(root, refs);
        assertThat(refs).as("문서에 $ref가 하나도 없으면 검사가 의미 없다").isNotEmpty();

        List<String> missing = refs.stream()
                .filter(ref -> !ref.startsWith("#/") || root.at(ref.substring(1)).isMissingNode())
                .toList();
        assertThat(missing).as("components에 없는 $ref").isEmpty();
    }

    private static void collectRefs(JsonNode node, Set<String> refs) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isString()) {
                refs.add(ref.asString());
            }
            node.values().forEach(child -> collectRefs(child, refs));
        } else if (node.isArray()) {
            node.values().forEach(child -> collectRefs(child, refs));
        }
    }

    @Test
    void 로그인_사용자와_CSRF_토큰은_요청_파라미터로_보이지_않는다() throws Exception {
        browser().get("/v3/api-docs")
                .andExpect(jsonPath("$.paths['/api/me'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/csrf'].get.parameters").doesNotExist());
    }

    @Test
    void Swagger_UI는_XSRF_TOKEN_쿠키를_X_XSRF_TOKEN_헤더로_붙인다() throws Exception {
        browser().get("/swagger-ui/index.html").andExpect(status().isOk());
        browser().get("/swagger-ui/swagger-initializer.js")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("XSRF-TOKEN=")))
                .andExpect(content().string(containsString("request.headers['X-XSRF-TOKEN']")));
    }
}
