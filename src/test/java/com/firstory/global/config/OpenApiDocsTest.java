package com.firstory.global.config;

import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .andExpect(jsonPath("$.paths['/api/stories/{id}/confirm'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/home'].get.summary").value("홈 (HOME-01)"))
                .andExpect(jsonPath("$.paths['/api/stories'].get.summary").value("서재 목록 (LIB-01)"))
                .andExpect(jsonPath("$.paths['/api/stories/{id}'].get.responses['404'].description",
                        containsString("STORY_NOT_FOUND")))
                .andExpect(jsonPath("$.paths['/api/stories/{id}/guide'].get.responses['409'].description",
                        containsString("INVALID_STATUS_TRANSITION")))
                .andExpect(jsonPath("$.paths['/api/stories/{id}/reading-sessions'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/stories/{id}/reading-sessions/resume'].get.responses['204']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/reading-sessions/{sessionId}/dialogue/{promptId}']"
                        + ".put.responses['404'].description", containsString("PROMPT_NOT_FOUND")))
                .andExpect(jsonPath("$.paths['/api/reading-sessions/{sessionId}/complete'].post.responses['204']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/stories/{id}/confirm'].post.responses['400'].description",
                        containsString("TONE_REQUIRED")))
                .andExpect(jsonPath("$.paths['/api/stories/{id}/regenerate'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/stories/{id}/regenerate'].post.responses['409'].description",
                        containsString("REGENERATION_LIMIT_REACHED")))
                .andExpect(jsonPath("$.paths['/api/stories/{id}/generation'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/stories/{id}/versions/{versionId}/pages/{pageNo}/image']"
                        + ".get.responses['200'].content['image/png']").exists())
                .andExpect(jsonPath("$.paths['/api/stories/{id}/versions/{versionId}/pages/{pageNo}/image']"
                        + ".get.responses['404'].description", containsString("IMAGE_NOT_FOUND")))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ValidationErrorResponse").exists());
    }

    /**
     * 문서 안의 모든 $ref가 components에 실제로 있는지 본다. 없으면 Swagger UI가 "Could not resolve reference"를 낸다.
     */
    @Test
    void 프록시_뒤에서는_문서의_서버_주소가_원래_https_주소다() throws Exception {
        // Railway처럼 https를 받아 http로 넘기는 프록시 (forward-headers-strategy: framework)
        mockMvc.perform(get("/v3/api-docs")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "api.example.com")
                        .header("X-Forwarded-Port", "443"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("https://api.example.com"));
    }

    @Test
    void 가입_동의_예시는_실제_동의_항목_키다() throws Exception {
        JsonNode consents = schemas().path("SignupRequest").path("properties").path("consents");

        assertThat(consents.path("example").isObject()).as("문자열이 아닌 JSON 객체 예시").isTrue();
        assertThat(names(consents.path("example"))).containsExactly("GUARDIAN", "TERMS", "PRIVACY", "SMS_NOTIFY");
        assertThat(consents.path("description").asString()).contains("GUARDIAN", "SMS_NOTIFY");
    }

    /**
     * Map 필드에 예시가 없으면 Swagger UI가 additionalProp1, additionalProp2…를 만들어 보여 준다.
     */
    @Test
    void Map_필드는_모두_예시가_있다() throws Exception {
        List<String> missing = new ArrayList<>();
        for (String schema : names(schemas())) {
            JsonNode properties = schemas().path(schema).path("properties");
            for (String property : names(properties)) {
                JsonNode node = properties.path(property);
                if (node.has("additionalProperties") && !node.path("example").isObject()) {
                    missing.add(schema + "." + property);
                }
            }
        }

        assertThat(missing).isEmpty();
    }

    @Test
    void 받지_않는_enum_값과_형식이_정해진_값은_예시가_실제로_쓸_수_있는_값이다() throws Exception {
        JsonNode schemas = schemas();

        JsonNode scope = schemas.path("RegenerateRequest").path("properties").path("scope");
        assertThat(scope.path("enum").valueStream().map(JsonNode::asString).toList())
                .as("다시 만들기는 ALL을 받지 않는다").containsExactly("CHARACTER_STORY", "TEXT", "IMAGES");
        assertThat(schemas.path("RegenerateRequest").path("properties").path("pages").path("example").isArray()).isTrue();
        assertThat(example(schemas, "ChildProfileRequest", "birthYm")).isEqualTo("2021-03");
        for (String request : List.of("SendCodeRequest", "VerifyCodeRequest", "SignupRequest", "ChangePhoneRequest")) {
            assertThat(example(schemas, request, "phone")).as(request).isEqualTo("010-1234-5678");
        }
        assertThat(example(schemas, "VerifyCodeRequest", "code")).isEqualTo("000000");
        assertThat(example(schemas, "SignupRequest", "password")).isEqualTo("firstory123");
        assertThat(schemas.path("ProgressRequest").path("properties").path("page").path("example").asInt()).isEqualTo(1);
    }

    private JsonNode schemas() throws Exception {
        String docs = browser().get("/v3/api-docs").andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(docs).path("components").path("schemas");
    }

    private static String example(JsonNode schemas, String schema, String property) {
        return schemas.path(schema).path("properties").path(property).path("example").asString();
    }

    private static List<String> names(JsonNode object) {
        return object.propertyNames().stream().toList();
    }

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
