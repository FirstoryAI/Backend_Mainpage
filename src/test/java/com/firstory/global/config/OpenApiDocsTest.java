package com.firstory.global.config;

import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

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
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ValidationErrorResponse").exists());
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
