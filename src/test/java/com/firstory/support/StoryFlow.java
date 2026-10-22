package com.firstory.support;

import com.firstory.domain.generation.service.GenerationPoller;
import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API로 동화를 원하는 단계까지 만든다 (FakeAiClient, 스케줄러 꺼짐). 로그인한 Browser로 부른다.
 * 처음 만들기는 pollOnce 5번이면 REVIEWING이 된다.
 */
public final class StoryFlow {

    public static final int POLLS_TO_REVIEW = 5;

    private StoryFlow() {
    }

    /**
     * 입력과 인터뷰를 마친 CONFIRMING 동화. 아이 정보를 "다음에도"로 저장한다 (호칭 아이 A, B…).
     */
    public static long confirming(Browser browser, String message) throws Exception {
        String body = browser.post("/api/stories", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.storyId")).longValue();
        browser.put(url(id) + "/situation", """
                {"timing": "PAST", "category": "FRIEND_CONFLICT", "description": "친구가 밀쳤어요",
                 "reactions": ["CRIED"], "emotions": ["SAD"]}""").andExpect(jsonPath("$.saved").value(true));
        browser.put(url(id) + "/child", """
                {"childId": null, "saveForNextTime": true,
                 "profile": {"birthYm": "2021-03", "gender": "FEMALE", "traits": ["SHY"], "interestCodes": ["DINOSAUR"]}}""")
                .andExpect(jsonPath("$.saved").value(true));
        browser.put(url(id) + "/message", """
                {"message": "%s"}""".formatted(message)).andExpect(jsonPath("$.saved").value(true));
        browser.post(url(id) + "/interview/start", null).andExpect(status().isOk());
        browser.post(url(id) + "/interview/answer", """
                {"skip": true}""").andExpect(jsonPath("$.status").value("CONFIRMING"));
        return id;
    }

    public static long reviewing(Browser browser, GenerationPoller poller) throws Exception {
        long id = confirming(browser, "용기를 주고 싶어요");
        browser.post(url(id) + "/confirm", null).andExpect(status().isAccepted());
        for (int i = 0; i < POLLS_TO_REVIEW; i++) {
            poller.pollOnce();
        }
        browser.get(url(id) + "/generation").andExpect(jsonPath("$.status").value("REVIEWING"));
        return id;
    }

    /**
     * 가입 → 동화 만들기 → 생성 → 확정까지 마친 COMPLETED 동화.
     */
    public static long completed(Browser browser, GenerationPoller poller) throws Exception {
        long id = reviewing(browser, poller);
        browser.post(url(id) + "/approve", null).andExpect(jsonPath("$.status").value("COMPLETED"));
        return id;
    }

    public static String url(long id) {
        return "/api/stories/" + id;
    }
}
