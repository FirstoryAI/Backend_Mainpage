package com.firstory.domain.child.controller;

import com.firstory.domain.child.repository.ChildRepository;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.repository.StoryRepository;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChildIntegrationTest extends IntegrationTest {

    static final String PROFILE = """
            {"birthYm": "2021-03", "gender": "FEMALE", "traits": ["SHY"], "interests": ["공룡"]}""";

    @Autowired
    UserRepository userRepository;

    @Autowired
    ChildRepository childRepository;

    @Autowired
    StoryRepository storyRepository;

    @Autowired
    TransactionTemplate tx;

    Browser parent;

    @BeforeEach
    void signUpParent() throws Exception {
        parent = signUp(newEmail(), newPhone());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        browser().get("/api/children")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 두_번_추가하면_아이_A_아이_B_순서로_나온다() throws Exception {
        parent.post("/api/children", PROFILE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("아이 A"))
                .andExpect(jsonPath("$.birthYm").value("2021-03"))
                .andExpect(jsonPath("$.ageMonths").isNumber());
        parent.post("/api/children", PROFILE)
                .andExpect(jsonPath("$.displayName").value("아이 B"));

        parent.get("/api/children")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].displayName", contains("아이 A", "아이 B")));
    }

    @Test
    void 아이_A를_지우고_추가하면_다시_아이_A() throws Exception {
        long a = create(PROFILE);
        create(PROFILE);

        parent.delete("/api/children/" + a).andExpect(status().isNoContent());

        parent.post("/api/children", PROFILE).andExpect(jsonPath("$.displayName").value("아이 A"));
    }

    @Test
    void 호칭은_1자에서_30자까지_바꿀_수_있다() throws Exception {
        long id = create(PROFILE);

        for (String invalid : new String[]{"", "   ", "가".repeat(31)}) {
            parent.patch("/api/children/" + id + "/display-name", """
                    {"displayName": "%s"}""".formatted(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CHILD_NAME"));
        }
        parent.patch("/api/children/" + id + "/display-name", """
                {"displayName": "첫째"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("첫째"));
        parent.get("/api/children/" + id).andExpect(jsonPath("$.displayName").value("첫째"));
    }

    @Test
    void 좋아하는_것이_6개면_INTERESTS_LIMIT_EXCEEDED() throws Exception {
        parent.post("/api/children", """
                {"birthYm": "2021-03", "gender": "MALE", "interests": ["1", "2", "3", "4", "5", "6"]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INTERESTS_LIMIT_EXCEEDED"));
    }

    @Test
    void 좋아하는_것은_공백을_지우고_중복을_합친다() throws Exception {
        parent.post("/api/children", """
                {"birthYm": "2021-03", "gender": "MALE", "interests": [" 공룡 ", "공룡", ""]}""")
                .andExpect(jsonPath("$.interests", contains("공룡")));
    }

    @Test
    void 성향과_좋아하는_것은_생략하면_빈_목록() throws Exception {
        parent.post("/api/children", """
                {"birthYm": "2021-03", "gender": "UNSPECIFIED"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.traits", hasSize(0)))
                .andExpect(jsonPath("$.interests", hasSize(0)));
    }

    @Test
    void 성향은_요청_순서와_상관없이_선언_순서로_내려준다() throws Exception {
        parent.post("/api/children", """
                {"birthYm": "2021-03", "gender": "MALE", "traits": ["STUBBORN", "SHY", "SENSITIVE", "SHY"]}""")
                .andExpect(jsonPath("$.traits", contains("SENSITIVE", "SHY", "STUBBORN")));
    }

    @Test
    void 잘못된_입력은_400() throws Exception {
        parent.post("/api/children", """
                {"birthYm": "2021-03", "gender": "CAT"}""")
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        parent.post("/api/children", """
                {"birthYm": "2021-03", "gender": "MALE", "traits": ["BRAVE"]}""")
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        parent.post("/api/children", """
                {"birthYm": "2021-3", "gender": "MALE"}""")
                .andExpect(jsonPath("$.code").value("INVALID_BIRTH_YM"));
        parent.post("/api/children", """
                {"gender": "MALE"}""")
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 프로필을_수정할_수_있다() throws Exception {
        long id = create(PROFILE);

        parent.put("/api/children/" + id, """
                {"birthYm": "2020-01", "gender": "MALE", "traits": ["ACTIVE"], "interests": ["기차"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("아이 A"))
                .andExpect(jsonPath("$.birthYm").value("2020-01"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.traits", contains("ACTIVE")))
                .andExpect(jsonPath("$.interests", contains("기차")));
    }

    @Test
    void 다른_회원의_아이는_조회_수정_호칭변경_삭제_모두_404() throws Exception {
        long othersChild = create(PROFILE);
        Browser stranger = signUp(newEmail(), newPhone());
        String url = "/api/children/" + othersChild;

        stranger.get(url).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CHILD_NOT_FOUND"));
        stranger.put(url, PROFILE).andExpect(status().isNotFound());
        stranger.patch(url + "/display-name", """
                {"displayName": "남의아이"}""").andExpect(status().isNotFound());
        stranger.delete(url).andExpect(status().isNotFound());
        stranger.get("/api/children").andExpect(jsonPath("$", hasSize(0)));

        parent.get(url).andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("아이 A"));
    }

    @Test
    void 없는_아이는_404() throws Exception {
        parent.get("/api/children/999999999").andExpect(status().isNotFound());
    }

    @Test
    void 삭제하면_목록에서_빠지고_연결된_동화는_남은_채_연결만_끊긴다() throws Exception {
        Long userId = userId(parent);
        long childId = create(PROFILE);
        Long storyId = tx.execute(status -> storyRepository.save(Story.start(
                userRepository.findById(userId).orElseThrow(),
                childRepository.findById(childId).orElseThrow(),
                UUID.randomUUID().toString())).getId());

        parent.delete("/api/children/" + childId).andExpect(status().isNoContent());

        parent.get("/api/children").andExpect(jsonPath("$", hasSize(0)));
        parent.get("/api/children/" + childId).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stories WHERE id = ?", Integer.class, storyId)).isOne();
        assertThat(jdbc.queryForObject("SELECT child_id FROM stories WHERE id = ?", Long.class, storyId)).isNull();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM children WHERE id = ?",
                Boolean.class, childId)).isTrue();
    }

    @Test
    void 선택지는_성향_7개와_라벨을_준다() throws Exception {
        parent.get("/api/children/options")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traits", hasSize(7)))
                .andExpect(jsonPath("$.traits[0].code").value("SENSITIVE"))
                .andExpect(jsonPath("$.traits[*].label", everyItem(not(emptyOrNullString()))))
                .andExpect(jsonPath("$.genders", hasSize(3)))
                .andExpect(jsonPath("$.interestsMax").value(5))
                .andExpect(jsonPath("$.interestMaxLength").value(20));
    }

    private long create(String profile) throws Exception {
        String body = parent.post("/api/children", profile)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
