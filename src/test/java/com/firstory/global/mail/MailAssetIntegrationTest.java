package com.firstory.global.mail;

import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MailAssetIntegrationTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Test
    void 메일_로고와_캐릭터는_로그인_없이_받는다() throws Exception {
        for (String name : new String[]{"logo", "mascot-verify", "mascot-reset", "mascot-started",
                "mascot-completed", "mascot-failed"}) {
            byte[] body = browser().get("/mail/" + name + ".png")
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(body).as(name).startsWith(PNG);
        }
    }

    @Test
    void 메일_폴더_밖은_그대로_막힌다() throws Exception {
        browser().get("/mail/../application.yml").andExpect(status().is4xxClientError());
        browser().get("/static/mail/logo.png").andExpect(status().is4xxClientError());
    }
}
