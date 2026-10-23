package com.firstory.global.health;

import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest extends IntegrationTest {

    @Test
    void 로그인_없이_ok() throws Exception {
        browser().get("/api/health")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
