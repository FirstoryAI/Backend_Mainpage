package com.firstory.global.ai;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.generation.entity.RegenerateScope;
import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.story.entity.ChildSnapshot;
import com.firstory.domain.story.entity.Emotion;
import com.firstory.domain.story.entity.InterviewEndReason;
import com.firstory.domain.story.entity.MomentReaction;
import com.firstory.domain.story.entity.SituationCategory;
import com.firstory.domain.story.entity.SituationTiming;
import com.firstory.domain.story.entity.StoryInput;
import com.firstory.domain.story.entity.Tone;
import com.firstory.domain.story.service.AiContextMapper;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.config.FirstoryProperties.Ai.AiMode;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.firstory.support.Fixtures.child;
import static com.firstory.support.Fixtures.story;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAiClientTest {

    static final String BASE_URL = "http://ai.test";
    static final String TOKEN = "internal-secret";
    static final String THREAD_ID = "3f1c2b9e-0000-4000-8000-000000000001";
    static final JsonMapper JSON = JsonMapper.builder().build();

    MockRestServiceServer server;
    HttpAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpAiClient(builder, new FirstoryProperties.Ai(AiMode.HTTP, BASE_URL, TOKEN,
                Duration.ofSeconds(3), Duration.ofSeconds(35), null));
    }

    @Test
    void 폼_위기_신호_검사() {
        server.expect(requestTo(BASE_URL + "/v1/safety/check"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpAiClient.TOKEN_HEADER, TOKEN))
                .andExpect(content().json("""
                        {"source": "form", "texts": ["어린이집에서 친구가 밀쳐서 넘어졌어요."]}""", JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"safety": {"level": "REVISE", "category": "harmful_discipline", "reason": "다시 써 주세요"}}""",
                        MediaType.APPLICATION_JSON));

        SafetyResult result = client.checkSafety(SafetySource.FORM, List.of("어린이집에서 친구가 밀쳐서 넘어졌어요."), null);

        assertThat(result).isEqualTo(new SafetyResult(SafetyLevel.REVISE, "harmful_discipline", "다시 써 주세요"));
        server.verify();
    }

    @Test
    void 인터뷰_답변_검사는_직전_질문을_context로_보낸다() {
        server.expect(requestTo(BASE_URL + "/v1/safety/check"))
                .andExpect(content().json("""
                        {"source": "interview_answer", "texts": ["대답을 안 해줬대요."],
                         "context": {"question": "그때 아이는 어떤 표정이었나요?"}}""", JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"safety": {"level": "OK", "category": null, "reason": null}}""", MediaType.APPLICATION_JSON));

        SafetyResult result = client.checkSafety(SafetySource.INTERVIEW, List.of("대답을 안 해줬대요."),
                "그때 아이는 어떤 표정이었나요?");

        assertThat(result).isEqualTo(SafetyResult.ok());
        server.verify();
    }

    @Test
    void source_값은_스펙과_같다() {
        assertThat(HttpAiClient.sourceValue(SafetySource.FORM)).isEqualTo("form");
        assertThat(HttpAiClient.sourceValue(SafetySource.INTERVIEW)).isEqualTo("interview_answer");
        assertThat(HttpAiClient.sourceValue(SafetySource.REGEN_FEEDBACK)).isEqualTo("regen_feedback");
    }

    @Test
    void 인터뷰_시작_요청은_스펙과_같은_키_구조이고_호칭이_없다() {
        UUID runId = UUID.randomUUID();
        List<String> bodies = new ArrayList<>();
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/interview/start"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpAiClient.TOKEN_HEADER, TOKEN))
                .andExpect(request -> bodies.add(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess("""
                        {"type": "question", "turn": 1, "question": "무슨 일이 있었나요?", "context_update": null}""",
                        MediaType.APPLICATION_JSON));

        InterviewReply reply = client.startInterview(THREAD_ID, runId, contextOfChildNamed("콩이"), Tone.WARM);

        assertThat(reply).isEqualTo(InterviewReply.question(1, "무슨 일이 있었나요?", null));
        String body = bodies.getFirst();
        assertThat(body).doesNotContain("콩이");
        JsonNode json = JSON.readTree(body);
        assertThat(json.propertyNames()).containsExactly("run_id", "context", "tone");
        assertThat(json.get("run_id").asString()).isEqualTo(runId.toString());
        assertThat(json.get("tone").asString()).isEqualTo("warm");
        JsonNode context = json.get("context");
        assertThat(context.propertyNames()).containsExactly("situation", "child", "moment", "parent");
        assertThat(context.get("situation").propertyNames())
                .containsExactly("category", "custom_category", "timing", "description");
        assertThat(context.get("child").propertyNames()).containsExactly("age_months", "gender", "traits",
                "custom_trait", "interests", "custom_interest");
        assertThat(context.get("moment").propertyNames()).containsExactly("reactions", "custom_reaction", "emotions");
        assertThat(context.get("parent").propertyNames())
                .containsExactly("response", "concern", "message", "message_direction");
        server.verify();
    }

    @Test
    void 분위기를_고르지_않았으면_tone은_null() {
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/interview/start"))
                .andExpect(request -> assertThat(JSON.readTree(((MockClientHttpRequest) request).getBodyAsString())
                        .get("tone").isNull()).isTrue())
                .andRespond(withSuccess("""
                        {"type": "question", "turn": 1, "question": "q"}""", MediaType.APPLICATION_JSON));

        client.startInterview(THREAD_ID, UUID.randomUUID(), contextOfChildNamed("콩이"), null);

        server.verify();
    }

    @Test
    void 답변을_보내면_다음_질문을_받는다() {
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/interview/answer"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpAiClient.TOKEN_HEADER, TOKEN))
                .andExpect(content().json("""
                        {"answer": "같이 놀자고 했는데 대답을 안 해줬대요."}""", JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"type": "question", "turn": 2, "question": "그때 아이는 어떤 표정이었나요?",
                         "context_update": {"moment": {"emotions": ["sad"]}}}""", MediaType.APPLICATION_JSON));

        InterviewReply reply = client.answerInterview(THREAD_ID, "같이 놀자고 했는데 대답을 안 해줬대요.");

        assertThat(reply.type()).isEqualTo(InterviewReply.Type.QUESTION);
        assertThat(reply.turn()).isEqualTo(2);
        assertThat(reply.question()).isEqualTo("그때 아이는 어떤 표정이었나요?");
        assertThat(reply.contextUpdate()).isEqualTo(Map.of("moment", Map.of("emotions", List.of("sad"))));
        server.verify();
    }

    @Test
    void 건너뛰기는_skip_true를_보내고_확인_응답을_받는다() {
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/interview/answer"))
                .andExpect(content().json("""
                        {"skip": true}""", JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"type": "confirm", "summary": "요약", "end_reason": "user_skip",
                         "context": {"parent": {"message_direction": "comfort_first"}}}""", MediaType.APPLICATION_JSON));

        InterviewReply reply = client.answerInterview(THREAD_ID, null);

        assertThat(reply.isConfirm()).isTrue();
        assertThat(reply.summary()).isEqualTo("요약");
        assertThat(reply.endReason()).isEqualTo(InterviewEndReason.USER_SKIP);
        assertThat(reply.finalContext()).isEqualTo(Map.of("parent", Map.of("message_direction", "comfort_first")));
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "500, INTERNAL_ERROR, AI_UNAVAILABLE",
            "503, , AI_UNAVAILABLE",
            "409, THREAD_BUSY, AI_BUSY",
            "409, INVALID_STATE, AI_STATE_MISMATCH",
            "404, THREAD_NOT_FOUND, AI_STATE_MISMATCH",
            "422, VALIDATION_ERROR, AI_UNAVAILABLE",
            "401, UNAUTHORIZED, AI_UNAVAILABLE"
    })
    void AI_오류_응답을_바꾼다(int status, String code, ErrorCode expected) {
        String body = code == null ? "" : """
                {"error": {"code": "%s", "message": "부모 입력이 담긴 메시지"}}""".formatted(code);
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/interview/answer"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(body));

        assertError(() -> client.answerInterview(THREAD_ID, "답변"), expected);
    }

    @Test
    void 타임아웃은_AI_UNAVAILABLE() {
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/interview/answer"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertError(() -> client.answerInterview(THREAD_ID, "답변"), ErrorCode.AI_UNAVAILABLE);
    }

    @Test
    void 연결_실패는_AI_UNAVAILABLE() {
        server.expect(requestTo(BASE_URL + "/v1/safety/check"))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertError(() -> client.checkSafety(SafetySource.FORM, List.of("글"), null), ErrorCode.AI_UNAVAILABLE);
    }

    @Test
    void 알_수_없는_응답_형식은_AI_UNAVAILABLE() {
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/interview/answer"))
                .andRespond(withSuccess("""
                        {"type": "unknown"}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/v1/safety/check"))
                .andRespond(withSuccess("""
                        {"safety": {"level": "DANGER"}}""", MediaType.APPLICATION_JSON));

        assertError(() -> client.answerInterview(THREAD_ID, "답변"), ErrorCode.AI_UNAVAILABLE);
        assertError(() -> client.checkSafety(SafetySource.FORM, List.of("글"), null), ErrorCode.AI_UNAVAILABLE);
    }

    @Test
    void 생성_시작은_run_정보와_tone을_보낸다() {
        UUID runId = UUID.fromString("6c1f0f7e-2b8a-4f53-9a6e-0f5b8f0c2d11");
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpAiClient.TOKEN_HEADER, TOKEN))
                .andExpect(content().json("""
                        {"run_id": "6c1f0f7e-2b8a-4f53-9a6e-0f5b8f0c2d11", "run_no": 1,
                         "output_prefix": "stories/123/runs/1/", "tone": "warm"}""", JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"accepted\": true}"));

        client.generate(THREAD_ID, runId, 1, "stories/123/runs/1/", Tone.WARM);

        server.verify();
    }

    @Test
    void 진행_상태를_읽는다() {
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/status"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpAiClient.TOKEN_HEADER, TOKEN))
                .andRespond(withSuccess("""
                        {"state": "RUNNING", "run_no": 1, "current_node": "illustrate", "stage": 3, "error": null,
                         "updated_at": "2026-10-21T05:30:00Z"}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/status"))
                .andRespond(withSuccess("""
                        {"state": "FAILED", "run_no": 1, "current_node": "illustrate", "stage": 3,
                         "error": {"code": "IMAGE_API_ERROR", "node": "illustrate", "message": "부모 입력"}}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.status(THREAD_ID))
                .isEqualTo(new AiGenerationStatus(AiRunState.RUNNING, 1, "illustrate", 3, null));
        assertThat(client.status(THREAD_ID).error()).isEqualTo("IMAGE_API_ERROR");
        server.verify();
    }

    @Test
    void 결과를_읽는다() {
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/result"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"run_no": 1, "checkpoint_id": "1ef8a", "title": "공룡 아로의 새 친구",
                         "design": {"hero": "아로"},
                         "character_sheets": [{"role": "main", "name": "아로", "path": "stories/123/runs/1/images/sheet-main-1.png"}],
                         "pages": [{"page_no": 1, "text": "아로는 **쿵쿵**", "image_prompt": "p",
                                    "image_path": "stories/123/runs/1/images/page-01.png", "image_status": "done"}],
                         "guide": {"before_reading": {"intent": "의도", "tips": ["팁"]},
                                   "prompts": [{"after_page": 3, "text": "질문", "purpose": "emotion", "parent_hint": "힌트"}],
                                   "after_reading": {"bridge_question": "다리", "if_undecided": "괜찮아요"}},
                         "cost": {"input_tokens": 41230, "output_tokens": 8120, "image_count": 9},
                         "safety": {"level": "OK", "category": null, "reason": null}}""", MediaType.APPLICATION_JSON));

        AiGenerationResult result = client.result(THREAD_ID);

        assertThat(result.runNo()).isEqualTo(1);
        assertThat(result.checkpointId()).isEqualTo("1ef8a");
        assertThat(result.design()).containsEntry("hero", "아로");
        assertThat(result.characterSheets()).containsExactly(
                new AiGenerationResult.CharacterSheet("main", "아로", "stories/123/runs/1/images/sheet-main-1.png"));
        assertThat(result.pages()).containsExactly(new AiGenerationResult.Page(1, "아로는 **쿵쿵**", "p",
                "stories/123/runs/1/images/page-01.png", "done"));
        assertThat(result.guide()).isEqualTo(new AiGenerationResult.Guide("의도", List.of("팁"), "다리", "괜찮아요",
                List.of(new AiGenerationResult.Prompt(3, "질문", "emotion", "힌트"))));
        assertThat(result.cost()).isEqualTo(new AiGenerationResult.Cost(41230, 8120, 9));
        assertThat(result.safety()).isEqualTo(SafetyResult.ok());
    }

    @Test
    void 가이드의_parent_hint와_if_undecided는_없어도_된다() {
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/result"))
                .andRespond(withSuccess("""
                        {"run_no": 1, "checkpoint_id": "cp", "title": "제목", "pages": [],
                         "guide": {"before_reading": {"intent": "의도", "tips": []},
                                   "prompts": [{"after_page": 3, "purpose": "emotion", "text": "질문"}],
                                   "after_reading": {"bridge_question": "다리"}}}""", MediaType.APPLICATION_JSON));

        AiGenerationResult.Guide guide = client.result(THREAD_ID).guide();

        assertThat(guide.ifUndecided()).isNull();
        assertThat(guide.prompts()).containsExactly(new AiGenerationResult.Prompt(3, "질문", "emotion", null));
    }

    @Test
    void 기준_checkpoint가_없으면_null로_보낸다() {
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/review"))
                .andExpect(request -> assertThat(JSON.readTree(((MockClientHttpRequest) request).getBodyAsString())
                        .get("base_checkpoint_id").isNull()).isTrue())
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.regenerate(THREAD_ID, RegenerateScope.CHARACTER_STORY, List.of(), null, null, UUID.randomUUID(), 2,
                "stories/123/runs/2/");

        server.verify();
    }

    @Test
    void 확정은_approve() {
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/review"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"action": "approve"}""", JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"state": "COMPLETED"}""", MediaType.APPLICATION_JSON));

        client.approve(THREAD_ID);

        server.verify();
    }

    @Test
    void 다시_만들기는_scope를_바꾸고_기준_checkpoint를_보낸다() {
        UUID runId = UUID.fromString("6c1f0f7e-2b8a-4f53-9a6e-0f5b8f0c2d12");
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/review"))
                .andExpect(content().json("""
                        {"action": "regenerate", "scope": "images", "pages": [3, 5], "feedback": null,
                         "base_checkpoint_id": "1ef8a", "run_id": "6c1f0f7e-2b8a-4f53-9a6e-0f5b8f0c2d12",
                         "run_no": 2, "output_prefix": "stories/123/runs/2/"}""", JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.regenerate(THREAD_ID, RegenerateScope.IMAGES, List.of(3, 5), null, "1ef8a", runId, 2,
                "stories/123/runs/2/");

        server.verify();
    }

    @Test
    void scope_값은_스펙과_같다() {
        assertThat(HttpAiClient.scopeValue(RegenerateScope.CHARACTER_STORY)).isEqualTo("story");
        assertThat(HttpAiClient.scopeValue(RegenerateScope.TEXT)).isEqualTo("text");
        assertThat(HttpAiClient.scopeValue(RegenerateScope.IMAGES)).isEqualTo("images");
        assertThatThrownBy(() -> HttpAiClient.scopeValue(RegenerateScope.ALL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 다시_시도는_retry() {
        UUID runId = UUID.fromString("6c1f0f7e-2b8a-4f53-9a6e-0f5b8f0c2d13");
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/retry"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"run_id": "6c1f0f7e-2b8a-4f53-9a6e-0f5b8f0c2d13", "run_no": 2,
                         "output_prefix": "stories/123/runs/2/", "tone": null}""", JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.retry(THREAD_ID, runId, 2, "stories/123/runs/2/");

        server.verify();
    }

    @Test
    void 생성_중인_thread에_다시_시작하면_AI_BUSY() {
        server.expect(requestTo(BASE_URL + "/v1/threads/" + THREAD_ID + "/generate"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": {"code": "THREAD_BUSY", "message": "busy"}}"""));

        assertError(() -> client.generate(THREAD_ID, UUID.randomUUID(), 1, "stories/1/runs/1/", null),
                ErrorCode.AI_BUSY);
    }

    private static Map<String, Object> contextOfChildNamed(String displayName) {
        Child child = child(user(), displayName);
        StoryInput input = StoryInput.begin(story(user()));
        input.writeSituation(SituationTiming.PAST, SituationCategory.FRIEND_CONFLICT, null, "친구가 밀쳤어요",
                List.of(MomentReaction.CRIED), null, List.of(Emotion.SAD));
        input.writeChild(ChildSnapshot.of(child, LocalDate.of(2026, 10, 1)));
        return AiContextMapper.toContext(input);
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
