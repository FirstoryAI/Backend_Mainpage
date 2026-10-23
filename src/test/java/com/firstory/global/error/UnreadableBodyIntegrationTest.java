package com.firstory.global.error;

import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 본문을 읽지 못한 VALIDATION_FAILED도 fields에 어느 필드가 문제인지 담는다.
 * 받은 값(원문)은 응답에도 로그에도 남기지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class UnreadableBodyIntegrationTest extends IntegrationTest {

    // 응답과 로그에 나오면 안 되는 원문 값
    static final String RAW = "RAWVALUE7Q";

    @Test
    void 모르는_Map_키는_그_Map_필드이고_키는_응답에_없다(CapturedOutput output) throws Exception {
        String body = """
                {"email": "a@example.com", "password": "firstory123", "phone": "010-1234-5678",
                 "consents": {"GUARDIAN": true, "%s": true}, "termsVersion": "2026-10-01"}""".formatted(RAW);

        assertField(browser().post("/api/auth/signup", body), "consents", "알 수 없는 항목이 있어요.", output);
    }

    @Test
    void Map_값의_형식이_틀리면_Map_필드까지만(CapturedOutput output) throws Exception {
        String body = """
                {"email": "a@example.com", "consents": {"TERMS": "%s"}}""".formatted(RAW);

        assertField(browser().post("/api/auth/signup", body), "consents", "형식이 맞지 않아요.", output);
    }

    @Test
    void 없는_enum_값은_그_필드(CapturedOutput output) throws Exception {
        String body = """
                {"phone": "010-1234-5678", "purpose": "%s"}""".formatted(RAW);

        assertField(browser().post("/api/auth/phone/send-code", body), "purpose", "고를 수 없는 값이에요.", output);
    }

    @Test
    void 안쪽_객체와_목록의_enum은_경로와_순번(CapturedOutput output) throws Exception {
        Browser parent = signUp(newEmail(), newPhone());
        String body = """
                {"birthYm": "2021-03", "gender": "FEMALE", "traits": ["SHY", "%s"]}""".formatted(RAW);

        assertField(parent.post("/api/children", body), "traits[1]", "고를 수 없는 값이에요.", output);

        String nested = """
                {"childId": null, "saveForNextTime": false, "profile": {"birthYm": "2021-03", "gender": "%s"}}"""
                .formatted(RAW);
        long storyId = newStory(parent);
        assertField(parent.put("/api/stories/" + storyId + "/child", nested), "profile.gender",
                "고를 수 없는 값이에요.", output);
    }

    @Test
    void 타입이_틀리면_형식이_맞지_않아요(CapturedOutput output) throws Exception {
        Browser parent = signUp(newEmail(), newPhone());
        long storyId = newStory(parent);

        assertField(parent.put("/api/stories/" + storyId + "/current-version", """
                {"versionId": "%s"}""".formatted(RAW)), "versionId", "형식이 맞지 않아요.", output);
    }

    @Test
    void 깨진_JSON은_깨진_곳의_필드(CapturedOutput output) throws Exception {
        String body = """
                {"email": "a@example.com", "consents": {"TERMS": %s""".formatted(RAW);

        assertField(browser().post("/api/auth/signup", body), "consents", "JSON 형식이 맞지 않아요.", output);
    }

    @Test
    void JSON이_아니면_body(CapturedOutput output) throws Exception {
        assertField(browser().post("/api/auth/login", RAW), "body", "JSON 형식이 맞지 않아요.", output);
    }

    @Test
    void 본문이_없으면_body(CapturedOutput output) throws Exception {
        assertField(browser().post("/api/auth/login", null), "body", "요청 내용이 없어요.", output);
    }

    private static long newStory(Browser parent) throws Exception {
        String body = parent.post("/api/stories", null).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.storyId")).longValue();
    }

    private static void assertField(ResultActions result, String field, String message, CapturedOutput output)
            throws Exception {
        String response = result
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields", hasSize(1)))
                .andExpect(jsonPath("$.fields[0].field").value(field))
                .andExpect(jsonPath("$.fields[0].message").value(message))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(RAW);
        assertThat(output.getAll()).doesNotContain(RAW);
    }
}
