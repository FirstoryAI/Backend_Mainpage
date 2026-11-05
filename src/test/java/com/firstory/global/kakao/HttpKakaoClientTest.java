package com.firstory.global.kakao;

import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.config.FirstoryProperties.Kakao.KakaoMode;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpKakaoClientTest {

    static final String AUTH = "https://kauth.test";
    static final String API = "https://kapi.test";
    static final String REDIRECT = "http://localhost:5173/oauth/kakao";

    MockRestServiceServer server;
    HttpKakaoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpKakaoClient(builder, AUTH, API,
                new FirstoryProperties.Kakao(KakaoMode.HTTP, "rest-key", "secret", List.of(REDIRECT)));
    }

    @Test
    void 인가_코드로_토큰을_받고_회원번호와_인증된_이메일을_읽는다() {
        server.expect(requestTo(AUTH + "/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("grant_type", "authorization_code",
                        "client_id", "rest-key", "redirect_uri", REDIRECT, "code", "the-code",
                        "client_secret", "secret")))
                .andRespond(withSuccess("""
                        {"access_token": "token-1", "token_type": "bearer"}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/v2/user/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("""
                        {"id": 1234567890, "kakao_account": {"has_email": true, "email": "parent@example.com",
                         "is_email_valid": true, "is_email_verified": true}}""", MediaType.APPLICATION_JSON));

        KakaoUser user = client.fetchUser("the-code", REDIRECT);

        assertThat(user).isEqualTo(new KakaoUser("1234567890", "parent@example.com", true));
        assertThat(user.toString()).doesNotContain("parent");
        server.verify();
    }

    @Test
    void 인증하지_않았거나_유효하지_않은_이메일은_emailVerified_false() {
        token();
        server.expect(requestTo(API + "/v2/user/me")).andRespond(withSuccess("""
                {"id": 1, "kakao_account": {"email": "a@example.com", "is_email_valid": false, "is_email_verified": true}}""",
                MediaType.APPLICATION_JSON));

        assertThat(client.fetchUser("c", REDIRECT)).isEqualTo(new KakaoUser("1", "a@example.com", false));
    }

    @Test
    void 이메일_동의를_안_했으면_email은_null() {
        token();
        server.expect(requestTo(API + "/v2/user/me")).andRespond(withSuccess("""
                {"id": 2, "kakao_account": {"email_needs_agreement": true}}""", MediaType.APPLICATION_JSON));

        assertThat(client.fetchUser("c", REDIRECT)).isEqualTo(new KakaoUser("2", null, false));
    }

    @Test
    void 코드가_만료됐거나_틀리면_KAKAO_LOGIN_FAILED() {
        server.expect(requestTo(AUTH + "/oauth/token")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"error": "invalid_grant", "error_code": "KOE320"}"""));

        assertThatThrownBy(() -> client.fetchUser("used", REDIRECT))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KAKAO_LOGIN_FAILED));
    }

    @Test
    void 카카오_서버_오류는_KAKAO_UNAVAILABLE() {
        token();
        server.expect(requestTo(API + "/v2/user/me")).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchUser("c", REDIRECT))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KAKAO_UNAVAILABLE));
    }

    private void token() {
        server.expect(requestTo(AUTH + "/oauth/token")).andRespond(withSuccess("""
                {"access_token": "t"}""", MediaType.APPLICATION_JSON));
    }
}
