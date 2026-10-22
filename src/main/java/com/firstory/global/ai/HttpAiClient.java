package com.firstory.global.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.story.entity.InterviewEndReason;
import com.firstory.domain.story.entity.Tone;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 진짜 AI 서버(FastAPI)를 부른다 (firstory.ai.mode=http). 경로와 JSON은 스펙 v0.3 그대로 (snake_case).
 *
 * <pre>
 * 연결 실패, 타임아웃, 5xx                   AI_UNAVAILABLE
 * 409 THREAD_BUSY                            AI_BUSY
 * 409 INVALID_STATE, 404 THREAD_NOT_FOUND    AI_STATE_MISMATCH (로그에 thread_id)
 * 422, 그 밖의 4xx, 응답 형식 오류            AI_UNAVAILABLE + 경고 로그 (우리 쪽 형식 버그)
 * </pre>
 * 요청·응답 본문에는 부모 입력과 아이 정보가 들어 있어 로그에 남기지 않는다. 오류 응답도 code만 남긴다.
 */
@Slf4j
public class HttpAiClient implements AiClient {

    static final String TOKEN_HEADER = "X-Internal-Token";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;

    HttpAiClient(RestClient.Builder builder, FirstoryProperties.Ai ai) {
        this.restClient = builder
                .baseUrl(ai.baseUrl())
                .defaultHeader(TOKEN_HEADER, ai.internalToken())
                .build();
    }

    public static HttpAiClient create(FirstoryProperties.Ai ai) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(ai.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(ai.readTimeout());
        return new HttpAiClient(RestClient.builder().requestFactory(requestFactory), ai);
    }

    @Override
    public SafetyResult checkSafety(SafetySource source, List<String> texts, String questionContext) {
        TransactionGuard.requireNoTransaction();
        SafetyCheckRequest request = new SafetyCheckRequest(sourceValue(source), texts,
                questionContext == null ? null : Map.of("question", questionContext));
        SafetyCheckResponse response = call(null, () -> restClient.post()
                .uri("/v1/safety/check")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SafetyCheckResponse.class));
        if (response == null || response.safety() == null || response.safety().level() == null) {
            throw invalidResponse(null, "safety");
        }
        SafetyBody safety = response.safety();
        try {
            return new SafetyResult(SafetyLevel.valueOf(safety.level().toUpperCase(Locale.ROOT)), safety.category(),
                    safety.reason());
        } catch (IllegalArgumentException e) {
            throw invalidResponse(null, "safety.level");
        }
    }

    @Override
    public InterviewReply startInterview(String threadId, UUID runId, Map<String, Object> context, Tone tone) {
        TransactionGuard.requireNoTransaction();
        StartInterviewRequest request = new StartInterviewRequest(runId, context, tone == null ? null : tone.aiValue());
        return toReply(threadId, call(threadId, () -> restClient.post()
                .uri("/v1/threads/{threadId}/interview/start", threadId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InterviewBody.class)));
    }

    @Override
    public InterviewReply answerInterview(String threadId, String answer) {
        TransactionGuard.requireNoTransaction();
        Map<String, Object> request = answer == null ? Map.of("skip", true) : Map.of("answer", answer);
        return toReply(threadId, call(threadId, () -> restClient.post()
                .uri("/v1/threads/{threadId}/interview/answer", threadId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InterviewBody.class)));
    }

    static String sourceValue(SafetySource source) {
        return switch (source) {
            case FORM -> "form";
            case INTERVIEW -> "interview_answer";
            case REGEN_FEEDBACK -> "regen_feedback";
            // 완성 본문은 result 응답에 safety가 함께 온다 (스펙 4장)
            case OUTPUT -> throw new IllegalArgumentException("완성 본문은 /safety/check로 검사하지 않아요");
        };
    }

    private <T> T call(String threadId, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw translate(threadId, e);
        } catch (ResourceAccessException e) {
            // 연결 실패, 타임아웃. 원인 종류만 남긴다
            log.warn("AI 서버에 연결하지 못했어요: thread_id={}, cause={}", threadId,
                    e.getMostSpecificCause().getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        } catch (RestClientException e) {
            // 응답 본문을 읽지 못함 (JSON 형식 오류 등)
            throw invalidResponse(threadId, e.getClass().getSimpleName());
        }
    }

    // e.getMessage()에는 응답 본문이 들어 있어 쓰지 않는다
    private static BusinessException translate(String threadId, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String code = errorCode(e.getResponseBodyAsByteArray());
        if (status == 409 && "THREAD_BUSY".equals(code)) {
            return new BusinessException(ErrorCode.AI_BUSY);
        }
        if (status == 409 || (status == 404 && "THREAD_NOT_FOUND".equals(code))) {
            log.warn("AI 진행 상태가 맞지 않아요: thread_id={}, status={}, code={}", threadId, status, code);
            return new BusinessException(ErrorCode.AI_STATE_MISMATCH);
        }
        if (status == 422) {
            log.warn("AI 서버가 요청 형식을 거부했어요. 보내는 형식을 확인해야 해요: thread_id={}, code={}", threadId, code);
        } else {
            log.warn("AI 서버 오류: thread_id={}, status={}, code={}", threadId, status, code);
        }
        return new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }

    private static String errorCode(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            ErrorBody error = JSON.readValue(body, ErrorBody.class);
            return error == null || error.error() == null ? null : error.error().code();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static InterviewReply toReply(String threadId, InterviewBody body) {
        if (body == null || body.type() == null) {
            throw invalidResponse(threadId, "type");
        }
        try {
            return switch (body.type()) {
                case "question" -> InterviewReply.question(body.turn(), body.question(), body.contextUpdate());
                case "confirm" -> InterviewReply.confirm(body.summary(),
                        InterviewEndReason.valueOf(body.endReason().toUpperCase(Locale.ROOT)), body.context());
                default -> throw new IllegalArgumentException("type");
            };
        } catch (NullPointerException | IllegalArgumentException e) {
            throw invalidResponse(threadId, "interview");
        }
    }

    private static BusinessException invalidResponse(String threadId, String field) {
        log.warn("AI 응답 형식이 맞지 않아요: thread_id={}, field={}", threadId, field);
        return new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }

    record SafetyCheckRequest(String source, List<String> texts,
                              @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> context) {
    }

    record StartInterviewRequest(@JsonProperty("run_id") UUID runId, Map<String, Object> context,
                                 String tone) {
    }

    record SafetyCheckResponse(SafetyBody safety) {
    }

    record SafetyBody(String level, String category, String reason) {
    }

    record InterviewBody(String type, Integer turn, String question,
                         @JsonProperty("context_update") Map<String, Object> contextUpdate,
                         String summary,
                         @JsonProperty("end_reason") String endReason,
                         Map<String, Object> context) {
    }

    record ErrorBody(ErrorDetail error) {
    }

    record ErrorDetail(String code, String message) {
    }
}
