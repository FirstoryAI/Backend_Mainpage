package com.firstory.global.config;

import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * local이 아닌 프로필(운영, test)에서는 Swagger UI와 API 문서가 열리지 않는다.
 */
class SwaggerDisabledTest extends IntegrationTest {

    @Test
    void API_문서와_Swagger_UI가_꺼져_있다() throws Exception {
        for (String url : new String[]{"/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html"}) {
            int status = browser().get(url).andReturn().getResponse().getStatus();
            assertThat(status).as(url).isEqualTo(404);
        }
    }
}
