package com.firstory.domain.story.controller;

import com.firstory.global.ai.FakeAiClient;
import com.firstory.global.error.ErrorCode;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 동화 만들기 CRT-01~05 전체 흐름. AI는 FakeAiClient (글에 [[SENSITIVE]], [[REVISE]], [[CRISIS]]가 있으면 그 level).
 */
class StoryCreationIntegrationTest extends IntegrationTest {

    static final String SITUATION = """
            {"timing": "PAST", "category": "FRIEND_CONFLICT", "description": "%s",
             "reactions": ["CRIED"], "emotions": ["SAD"]}""";
    static final String CHILD = """
            {"childId": null, "saveForNextTime": %s,
             "profile": {"birthYm": "2021-03", "gender": "FEMALE", "traits": ["SHY"], "interestCodes": ["DINOSAUR"],
                         "customInterests": ["%s"]}}""";
    static final String REVISE = "[[REVISE]]";
    static final String CRISIS = "[[CRISIS]]";
    static final String SENSITIVE = "[[SENSITIVE]]";

    @Autowired
    FakeAiClient ai;

    Browser parent;

    @BeforeEach
    void signUpParent() throws Exception {
        parent = signUp(newEmail(), newPhone());
    }

    @AfterEach
    void recoverAi() {
        ai.clearFailure();
    }

    @Test
    void 정상_흐름은_인터뷰_3번_답하면_생성_전_확인() throws Exception {
        long id = create();
        writeSituation(id, "친구가 밀쳐서 넘어졌어요")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storyId").value(id))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.safety.action").value("PROCEED"))
                .andExpect(jsonPath("$.safety.level").value("OK"))
                .andExpect(jsonPath("$.toneRequired").value(false));
        writeChild(id, false, "기차").andExpect(jsonPath("$.saved").value(true));
        writeMessage(id, "친구에게 먼저 말을 걸어보는 용기를 주고 싶어요").andExpect(jsonPath("$.saved").value(true));

        start(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEWING"))
                .andExpect(jsonPath("$.type").value("QUESTION"))
                .andExpect(jsonPath("$.turn").value(1))
                .andExpect(jsonPath("$.question").value(FakeAiClient.FIRST_QUESTION))
                .andExpect(jsonPath("$.summary").value(nullValue()))
                .andExpect(jsonPath("$.safety").value(nullValue()));
        answer(id, "같이 놀자고 했는데 대답을 안 해줬대요.").andExpect(jsonPath("$.turn").value(2))
                .andExpect(jsonPath("$.safety.action").value("PROCEED"));
        answer(id, "울면서 왔어요.").andExpect(jsonPath("$.turn").value(3));
        answer(id, "괜찮다고 말해 주고 싶어요.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMING"))
                .andExpect(jsonPath("$.type").value("CONFIRM"))
                .andExpect(jsonPath("$.summary").value(FakeAiClient.SUMMARY))
                .andExpect(jsonPath("$.turn").value(nullValue()));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_turns WHERE story_id = ? AND answer IS NOT NULL",
                Integer.class, id)).isEqualTo(3);
        Map<String, Object> input = jdbc.queryForMap(
                "SELECT final_context::text AS final_context, message_direction, parent_message, interview_end_reason "
                        + "FROM story_inputs WHERE story_id = ?", id);
        assertThat((String) input.get("final_context")).contains("\"message_direction\": \"comfort_first\"");
        assertThat(input.get("message_direction")).isEqualTo("COMFORT_FIRST");
        assertThat(input.get("parent_message")).isEqualTo("친구에게 먼저 말을 걸어보는 용기를 주고 싶어요");
        assertThat(input.get("interview_end_reason")).isEqualTo("SUFFICIENT");
    }

    @Test
    void 건너뛰면_USER_SKIP으로_끝난다() throws Exception {
        long id = readyToInterview();
        start(id);
        answer(id, "답변 1");

        skip(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMING"))
                .andExpect(jsonPath("$.type").value("CONFIRM"))
                .andExpect(jsonPath("$.safety").value(nullValue()));

        parent.get(draftUrl(id))
                .andExpect(jsonPath("$.interview.endReason").value("USER_SKIP"))
                .andExpect(jsonPath("$.interview.turns[*].answer", contains("답변 1", null)));
    }

    @Test
    void 답변과_건너뛰기는_하나만_보낸다() throws Exception {
        long id = readyToInterview();
        start(id);

        parent.post(interviewUrl(id, "answer"), """
                {"answer": "답변", "skip": true}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        parent.post(interviewUrl(id, "answer"), "{}").andExpect(status().isBadRequest());
        parent.post(interviewUrl(id, "answer"), """
                {"answer": "%s"}""".formatted("가".repeat(501))).andExpect(status().isBadRequest());
    }

    @Test
    void 폼에서_REVISE_1번째는_저장하지_않고_다시_쓰기_안내() throws Exception {
        long id = create();

        writeSituation(id, "말 안 들으면 혼난다고 " + REVISE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.safety.action").value("ASK_REVISE"))
                .andExpect(jsonPath("$.safety.level").value("REVISE"))
                .andExpect(jsonPath("$.safety.reason").isNotEmpty())
                .andExpect(jsonPath("$.safety.helpKind").value(nullValue()));

        parent.get(draftUrl(id)).andExpect(jsonPath("$.situation").value(nullValue()));
        assertThat(flags(id, "REVISE")).isOne();
        assertThat(jdbc.queryForObject("SELECT source FROM safety_flags WHERE story_id = ?", String.class, id))
                .isEqualTo("FORM");
    }

    @Test
    void REVISE_2번째는_다른_단계라도_차단하고_이후_모든_변경을_막는다() throws Exception {
        long id = create();
        writeSituation(id, REVISE).andExpect(jsonPath("$.safety.action").value("ASK_REVISE"));

        writeMessage(id, "겁을 줘서 " + REVISE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.safety.action").value("BLOCK"))
                .andExpect(jsonPath("$.safety.helpKind").value("PARENTING"));

        assertThat(flags(id, "REVISE")).isEqualTo(2);
        assertThat(storyStatus(id)).isEqualTo("BLOCKED");
        assertError(writeSituation(id, "평범한 글"), ErrorCode.STORY_BLOCKED);
        assertError(writeChild(id, false, "기차"), ErrorCode.STORY_BLOCKED);
        assertError(writeMessage(id, "평범한 글"), ErrorCode.STORY_BLOCKED);
        assertError(parent.put(storyUrl(id) + "/tone", """
                {"tone": "WARM"}"""), ErrorCode.STORY_BLOCKED);
        assertError(start(id), ErrorCode.STORY_BLOCKED);
        assertError(answer(id, "답변"), ErrorCode.STORY_BLOCKED);
        parent.get(draftUrl(id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void 인터뷰_답변이_CRISIS면_바로_차단하고_답변은_저장하지_않는다() throws Exception {
        long id = readyToInterview();
        start(id);

        answer(id, "아이를 때렸어요 " + CRISIS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.type").value(nullValue()))
                .andExpect(jsonPath("$.question").value(nullValue()))
                .andExpect(jsonPath("$.safety.action").value("BLOCK"))
                .andExpect(jsonPath("$.safety.level").value("CRISIS"))
                .andExpect(jsonPath("$.safety.helpKind").value("CHILD_SAFETY"));

        assertThat(storyStatus(id)).isEqualTo("BLOCKED");
        Map<String, Object> flag = jdbc.queryForMap(
                "SELECT f.source, f.level, f.interview_turn_id, t.turn_no FROM safety_flags f "
                        + "JOIN interview_turns t ON t.id = f.interview_turn_id WHERE f.story_id = ?", id);
        assertThat(flag.get("source")).isEqualTo("INTERVIEW");
        assertThat(flag.get("level")).isEqualTo("CRISIS");
        assertThat(flag.get("turn_no")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_turns WHERE story_id = ? AND answer IS NOT NULL",
                Integer.class, id)).isZero();
        assertThat(ai.answerCalls(threadId(id))).isZero();
    }

    @Test
    void 인터뷰_답변이_REVISE면_저장하지_않고_AI에_보내지_않고_같은_질문() throws Exception {
        long id = readyToInterview();
        start(id);

        answer(id, "혼내 주고 싶어요 " + REVISE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEWING"))
                .andExpect(jsonPath("$.type").value("QUESTION"))
                .andExpect(jsonPath("$.turn").value(1))
                .andExpect(jsonPath("$.question").value(FakeAiClient.FIRST_QUESTION))
                .andExpect(jsonPath("$.safety.action").value("ASK_REVISE"))
                .andExpect(jsonPath("$.safety.reason").isNotEmpty());

        assertThat(ai.answerCalls(threadId(id))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_turns WHERE story_id = ?", Integer.class, id))
                .isOne();
        assertThat(jdbc.queryForObject("SELECT answer FROM interview_turns WHERE story_id = ?", String.class, id))
                .isNull();

        // 다시 쓴 답변은 그대로 진행한다
        answer(id, "다시 쓴 답변").andExpect(jsonPath("$.turn").value(2));
        assertThat(ai.answerCalls(threadId(id))).isOne();
    }

    @Test
    void SENSITIVE면_분위기_선택을_요청하고_고른_뒤에는_진행() throws Exception {
        long id = create();

        writeSituation(id, "할머니가 돌아가셨어요 " + SENSITIVE)
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.safety.action").value("CHOOSE_TONE"))
                .andExpect(jsonPath("$.safety.level").value("SENSITIVE"))
                .andExpect(jsonPath("$.toneRequired").value(true));
        assertThat(flags(id, "SENSITIVE")).isZero();

        parent.put(storyUrl(id) + "/tone", """
                {"tone": "WARM"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.safety").value(nullValue()))
                .andExpect(jsonPath("$.toneRequired").value(true));

        writeMessage(id, "할머니 이야기 " + SENSITIVE)
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.safety.action").value("PROCEED"))
                .andExpect(jsonPath("$.safety.level").value("SENSITIVE"));
        parent.get(draftUrl(id))
                .andExpect(jsonPath("$.tone").value("WARM"))
                .andExpect(jsonPath("$.toneRequired").value(true));
    }

    @Test
    void 인터뷰_답변이_SENSITIVE면_진행하면서_분위기_선택을_요청() throws Exception {
        long id = readyToInterview();
        start(id).andExpect(jsonPath("$.toneRequired").value(false));

        answer(id, "강아지가 하늘나라에 갔어요 " + SENSITIVE)
                .andExpect(jsonPath("$.status").value("INTERVIEWING"))
                .andExpect(jsonPath("$.type").value("QUESTION"))
                .andExpect(jsonPath("$.turn").value(2))
                .andExpect(jsonPath("$.safety.action").value("CHOOSE_TONE"))
                .andExpect(jsonPath("$.toneRequired").value(true));

        // 인터뷰 중에도 분위기를 고를 수 있다
        parent.put(storyUrl(id) + "/tone", """
                {"tone": "PLAIN"}""").andExpect(status().isOk());
        answer(id, "또 " + SENSITIVE).andExpect(jsonPath("$.safety.action").value("PROCEED"));
    }

    @Test
    void 새_동화는_REVISE_횟수를_0부터_센다() throws Exception {
        long blocked = create();
        writeSituation(blocked, REVISE);
        writeMessage(blocked, REVISE).andExpect(jsonPath("$.status").value("BLOCKED"));

        long fresh = create();

        writeSituation(fresh, REVISE)
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.safety.action").value("ASK_REVISE"));
    }

    @Test
    void 인터뷰_중에는_입력을_바꿀_수_없다() throws Exception {
        long id = readyToInterview();
        start(id);

        assertError(writeSituation(id, "바꾼 상황"), ErrorCode.INVALID_STATUS_TRANSITION);
        assertError(start(id), ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void 상황이나_아이_정보_없이_시작하면_STORY_INPUT_INCOMPLETE() throws Exception {
        long id = create();
        assertError(start(id), ErrorCode.STORY_INPUT_INCOMPLETE);

        writeSituation(id, "상황");
        assertError(start(id), ErrorCode.STORY_INPUT_INCOMPLETE);

        writeChild(id, false, "기차");
        start(id).andExpect(status().isOk());
    }

    @Test
    void 인터뷰_전에는_답변할_수_없다() throws Exception {
        long id = readyToInterview();

        assertError(answer(id, "답변"), ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void 형식_오류는_AI를_부르기_전에_400() throws Exception {
        long id = create();
        ai.failWith(ErrorCode.AI_UNAVAILABLE);

        assertError(parent.put(storyUrl(id) + "/situation", """
                {"timing": "UPCOMING", "category": "NEW_ENVIRONMENT", "description": "이사", "reactions": ["CRIED"]}"""),
                ErrorCode.MOMENT_ONLY_FOR_PAST);
        assertError(parent.put(storyUrl(id) + "/situation", """
                {"timing": "PAST", "category": "OTHER", "description": "설명"}"""), ErrorCode.CUSTOM_VALUE_REQUIRED);
        assertError(writeSituation(id, "가".repeat(61)), ErrorCode.DESCRIPTION_TOO_LONG);
        assertError(writeChild(id, false, "가".repeat(21)), ErrorCode.CUSTOM_VALUE_TOO_LONG);
    }

    @Test
    void 다른_회원의_동화는_모든_API가_404() throws Exception {
        long id = readyToInterview();
        Browser other = signUp(newEmail(), newPhone());

        assertError(other.put(storyUrl(id) + "/situation", SITUATION.formatted("상황")), ErrorCode.STORY_NOT_FOUND);
        assertError(other.put(storyUrl(id) + "/child", CHILD.formatted(false, "기차")), ErrorCode.STORY_NOT_FOUND);
        assertError(other.put(storyUrl(id) + "/message", """
                {"message": "말"}"""), ErrorCode.STORY_NOT_FOUND);
        assertError(other.put(storyUrl(id) + "/tone", """
                {"tone": "WARM"}"""), ErrorCode.STORY_NOT_FOUND);
        assertError(other.get(draftUrl(id)), ErrorCode.STORY_NOT_FOUND);
        assertError(other.post(interviewUrl(id, "start"), null), ErrorCode.STORY_NOT_FOUND);
        assertError(other.post(interviewUrl(id, "answer"), """
                {"answer": "답변"}"""), ErrorCode.STORY_NOT_FOUND);
        assertError(other.delete(storyUrl(id)), ErrorCode.STORY_NOT_FOUND);
        assertThat(storyStatus(id)).isEqualTo("DRAFT");
    }

    @Test
    void 다른_회원의_아이를_고르면_CHILD_NOT_FOUND() throws Exception {
        Browser other = signUp(newEmail(), newPhone());
        long othersChild = ((Number) JsonPath.read(other.post("/api/children", """
                {"birthYm": "2021-03", "gender": "MALE"}""").andReturn().getResponse().getContentAsString(), "$.id"))
                .longValue();
        long id = create();

        assertError(parent.put(storyUrl(id) + "/child", """
                {"childId": %d, "saveForNextTime": false, "profile": {"birthYm": "2021-03", "gender": "MALE"}}"""
                .formatted(othersChild)), ErrorCode.CHILD_NOT_FOUND);
    }

    @Test
    void AI_장애면_503이고_답변과_차례를_저장하지_않는다() throws Exception {
        long id = readyToInterview();
        start(id);
        ai.failWith(ErrorCode.AI_UNAVAILABLE);

        assertError(answer(id, "답변"), ErrorCode.AI_UNAVAILABLE);
        assertError(writeSituation(create(), "상황"), ErrorCode.AI_UNAVAILABLE);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM interview_turns WHERE story_id = ?", Integer.class, id))
                .isOne();
        assertThat(jdbc.queryForObject("SELECT answer FROM interview_turns WHERE story_id = ?", String.class, id))
                .isNull();
        assertThat(storyStatus(id)).isEqualTo("INTERVIEWING");

        // 복구되면 같은 답변으로 다시 보낼 수 있다
        ai.clearFailure();
        answer(id, "답변").andExpect(status().isOk()).andExpect(jsonPath("$.turn").value(2));
    }

    @Test
    void 이어서_만들기_조회에_입력과_인터뷰가_모두_보인다() throws Exception {
        long id = create();
        writeSituation(id, "친구가 밀쳤어요");
        writeChild(id, true, "기차");
        writeMessage(id, "용기를 주고 싶어요");
        start(id);
        answer(id, "답변 1");
        skip(id);

        parent.get(draftUrl(id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storyId").value(id))
                .andExpect(jsonPath("$.status").value("CONFIRMING"))
                .andExpect(jsonPath("$.situation.timing").value("PAST"))
                .andExpect(jsonPath("$.situation.category").value("FRIEND_CONFLICT"))
                .andExpect(jsonPath("$.situation.description").value("친구가 밀쳤어요"))
                .andExpect(jsonPath("$.situation.reactions", contains("CRIED")))
                .andExpect(jsonPath("$.situation.emotions", contains("SAD")))
                .andExpect(jsonPath("$.child.childId").isNumber())
                .andExpect(jsonPath("$.child.displayName").value("아이 A"))
                .andExpect(jsonPath("$.child.gender").value("FEMALE"))
                .andExpect(jsonPath("$.child.ageMonths").isNumber())
                .andExpect(jsonPath("$.child.customInterests", contains("기차")))
                .andExpect(jsonPath("$.parent.message").value("용기를 주고 싶어요"))
                .andExpect(jsonPath("$.parent.messageDirection").value("COMFORT_FIRST"))
                .andExpect(jsonPath("$.interview.turns[*].question",
                        contains(FakeAiClient.FIRST_QUESTION, "그때 아이는 어떤 표정이었나요?")))
                .andExpect(jsonPath("$.interview.summary").value(FakeAiClient.SUMMARY))
                .andExpect(jsonPath("$.interview.endReason").value("USER_SKIP"))
                .andExpect(jsonPath("$.tone").value(nullValue()))
                .andExpect(jsonPath("$.toneRequired").value(false));
    }

    @Test
    void 저장하지_않은_아이는_호칭_없이_보인다() throws Exception {
        long id = create();
        writeChild(id, false, "기차");

        parent.get(draftUrl(id))
                .andExpect(jsonPath("$.child.childId").value(nullValue()))
                .andExpect(jsonPath("$.child.displayName").value(nullValue()));
        parent.get("/api/children").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 만들던_동화를_지우면_조회되지_않는다() throws Exception {
        long id = create();
        writeSituation(id, "상황");

        parent.delete(storyUrl(id)).andExpect(status().isNoContent());

        assertError(parent.get(draftUrl(id)), ErrorCode.STORY_NOT_FOUND);
        assertError(writeSituation(id, "상황"), ErrorCode.STORY_NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM stories WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    @Test
    void 차단된_동화도_지울_수_있다() throws Exception {
        long id = create();
        writeSituation(id, CRISIS).andExpect(jsonPath("$.status").value("BLOCKED"));

        parent.delete(storyUrl(id)).andExpect(status().isNoContent());
    }

    @Test
    void 새_동화는_201과_DRAFT() throws Exception {
        parent.post("/api/stories", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storyId").isNumber())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void AI로_보내는_context에_호칭이_없다() throws Exception {
        long id = readyToInterview();
        writeChild(id, true, "기차");
        long childId = ((Number) JsonPath.read(parent.get(draftUrl(id)).andReturn().getResponse().getContentAsString(),
                "$.child.childId")).longValue();
        parent.patch("/api/children/" + childId + "/display-name", """
                {"displayName": "콩이"}""").andExpect(status().isOk());
        parent.get(draftUrl(id)).andExpect(jsonPath("$.child.displayName").value("콩이"));

        start(id).andExpect(status().isOk());
        skip(id).andExpect(jsonPath("$.status").value("CONFIRMING"));

        // FakeAiClient는 받은 context에 message_direction만 채워 돌려준다. 그 안에 호칭이 없어야 한다
        String finalContext = jdbc.queryForObject("SELECT final_context::text FROM story_inputs WHERE story_id = ?",
                String.class, id);
        assertThat(finalContext).contains("\"age_months\"").doesNotContain("콩이").doesNotContain("display");
    }

    private long create() throws Exception {
        String body = parent.post("/api/stories", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.storyId")).longValue();
    }

    private long readyToInterview() throws Exception {
        long id = create();
        writeSituation(id, "친구가 밀쳤어요").andExpect(jsonPath("$.saved").value(true));
        writeChild(id, false, "기차").andExpect(jsonPath("$.saved").value(true));
        return id;
    }

    private ResultActions writeSituation(long id, String description) throws Exception {
        return parent.put(storyUrl(id) + "/situation", SITUATION.formatted(description));
    }

    private ResultActions writeChild(long id, boolean save, String customInterest) throws Exception {
        return parent.put(storyUrl(id) + "/child", CHILD.formatted(save, customInterest));
    }

    private ResultActions writeMessage(long id, String message) throws Exception {
        return parent.put(storyUrl(id) + "/message", """
                {"message": "%s"}""".formatted(message));
    }

    private ResultActions start(long id) throws Exception {
        return parent.post(interviewUrl(id, "start"), null);
    }

    private ResultActions answer(long id, String answer) throws Exception {
        return parent.post(interviewUrl(id, "answer"), """
                {"answer": "%s"}""".formatted(answer));
    }

    private ResultActions skip(long id) throws Exception {
        return parent.post(interviewUrl(id, "answer"), """
                {"skip": true}""");
    }

    private static String storyUrl(long id) {
        return "/api/stories/" + id;
    }

    private static String draftUrl(long id) {
        return storyUrl(id) + "/draft";
    }

    private static String interviewUrl(long id, String action) {
        return storyUrl(id) + "/interview/" + action;
    }

    private int flags(long id, String level) {
        return jdbc.queryForObject("SELECT count(*) FROM safety_flags WHERE story_id = ? AND level = ?",
                Integer.class, id, level);
    }

    private String storyStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM stories WHERE id = ?", String.class, id);
    }

    private String threadId(long id) {
        return jdbc.queryForObject("SELECT thread_id FROM stories WHERE id = ?", String.class, id);
    }

    private static void assertError(ResultActions result, ErrorCode expected) throws Exception {
        result.andExpect(status().is(expected.getStatus().value()))
                .andExpect(jsonPath("$.code").value(expected.name()))
                .andExpect(jsonPath("$.message", containsString(expected.getMessage().substring(0, 5))));
    }
}
