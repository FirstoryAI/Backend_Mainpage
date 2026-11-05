package com.firstory.global.kakao;

import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * 진짜 카카오 (firstory.kakao.mode=http).
 * <ol>
 *   <li>POST https://kauth.kakao.com/oauth/token (인가 코드 → 액세스 토큰)</li>
 *   <li>GET https://kapi.kakao.com/v2/user/me (회원번호, 이메일)</li>
 * </ol>
 * 토큰은 이 요청에서만 쓰고 저장하지 않는다. 응답 본문(이메일)은 로그에 남기지 않는다.
 */
@Slf4j
public class HttpKakaoClient implements KakaoClient {

    static final String AUTH_BASE_URL = "https://kauth.kakao.com";
    static final String API_BASE_URL = "https://kapi.kakao.com";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient authClient;
    private final RestClient apiClient;
    private final FirstoryProperties.Kakao kakao;

    HttpKakaoClient(RestClient.Builder builder, String authBaseUrl, String apiBaseUrl, FirstoryProperties.Kakao kakao) {
        this.authClient = builder.clone().baseUrl(authBaseUrl).build();
        this.apiClient = builder.clone().baseUrl(apiBaseUrl).build();
        this.kakao = kakao;
    }

    public static HttpKakaoClient create(FirstoryProperties.Kakao kakao) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new HttpKakaoClient(RestClient.builder().requestFactory(factory), AUTH_BASE_URL, API_BASE_URL, kakao);
    }

    @Override
    public KakaoUser fetchUser(String authorizationCode, String redirectUri) {
        String accessToken = token(authorizationCode, redirectUri);
        JsonNode me = call("user/me", () -> apiClient.get()
                .uri("/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class));
        if (me == null || !me.path("id").isNumber()) {
            log.warn("카카오 회원 응답에 id가 없어요");
            throw new BusinessException(ErrorCode.KAKAO_UNAVAILABLE);
        }
        JsonNode account = me.path("kakao_account");
        String email = account.path("email").isString() ? account.path("email").asString() : null;
        boolean verified = account.path("is_email_valid").asBoolean(false)
                && account.path("is_email_verified").asBoolean(false);
        return new KakaoUser(me.path("id").asString(), email, verified);
    }

    private String token(String authorizationCode, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", kakao.restApiKey());
        form.add("redirect_uri", redirectUri);
        form.add("code", authorizationCode);
        if (kakao.clientSecret() != null) {
            form.add("client_secret", kakao.clientSecret());
        }
        JsonNode body = call("token", () -> authClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));
        if (body == null || !body.path("access_token").isString()) {
            log.warn("카카오 토큰 응답에 access_token이 없어요");
            throw new BusinessException(ErrorCode.KAKAO_UNAVAILABLE);
        }
        return body.path("access_token").asString();
    }

    private static JsonNode call(String step, Supplier<JsonNode> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                // 코드 만료·재사용, redirect_uri 불일치, 앱 키 오류 등. 카카오 오류 code만 남긴다
                log.warn("카카오 {} 요청이 거절됐어요: status={}, error={}", step, e.getStatusCode().value(),
                        errorCode(e));
                throw new BusinessException(ErrorCode.KAKAO_LOGIN_FAILED);
            }
            log.warn("카카오 {} 요청 실패: status={}", step, e.getStatusCode().value());
            throw new BusinessException(ErrorCode.KAKAO_UNAVAILABLE);
        } catch (RestClientException e) {
            log.warn("카카오 {} 요청 실패: cause={}", step, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.KAKAO_UNAVAILABLE);
        }
    }

    // 카카오 오류 본문: {"error": "invalid_grant", "error_code": "KOE320", ...}. 그 밖의 내용은 쓰지 않는다
    private static String errorCode(RestClientResponseException e) {
        byte[] body = e.getResponseBodyAsByteArray();
        if (body.length == 0) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(body);
            JsonNode code = node.has("error_code") ? node.path("error_code") : node.path("error");
            return code.isString() ? code.asString() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
