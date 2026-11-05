package com.firstory.support;

import com.firstory.global.kakao.FakeKakaoClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 Security 필터, Spring Session JDBC, Flyway 마이그레이션이 모두 적용된 상태에서 도는 통합 테스트의 기반.
 * 테스트마다 다른 이메일을 써서 서로 영향을 주지 않는다. 메일은 RecordingEmailSender, 카카오는 FakeKakaoClient.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestBeans.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "firstory123!";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(systemNow());
        }

        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected RecordingEmailSender mail;

    @Autowired
    protected FakeKakaoClient kakao;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetClock() {
        clock.setInstant(systemNow());
        mail.failWith(null);
    }

    // timestamptz는 마이크로초까지 반올림해 저장한다. 시계도 마이크로초에 맞춰야 경계 시각(정확히 15분 뒤 등) 비교가 흔들리지 않는다
    private static Instant systemNow() {
        return Clock.systemUTC().instant().truncatedTo(ChronoUnit.MICROS);
    }

    protected Browser browser() throws Exception {
        return new Browser(mockMvc).fetchCsrf();
    }

    protected static String newEmail() {
        return "parent" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
    }

    protected void sendCode(Browser browser, String email, String purpose) throws Exception {
        browser.post("/api/auth/email/send-code", """
                {"email": "%s", "purpose": "%s"}""".formatted(email, purpose))
                .andExpect(status().isOk());
    }

    protected void verifyEmail(Browser browser, String email, String purpose) throws Exception {
        sendCode(browser, email, purpose);
        browser.post("/api/auth/email/verify", """
                {"email": "%s", "code": "%s"}""".formatted(email, mail.lastCode(email)))
                .andExpect(status().isNoContent());
    }

    protected static String signupJson(String email, String consents) {
        return """
                {"email": "%s", "password": "%s", "consents": %s, "termsVersion": "2026-10-01"}"""
                .formatted(email, PASSWORD, consents);
    }

    protected static String allConsents() {
        return """
                {"GUARDIAN": true, "TERMS": true, "PRIVACY": true, "EMAIL_NOTIFY": false}""";
    }

    /**
     * 이메일 인증부터 가입까지 마친 브라우저. 가입 직후 로그인된 상태다.
     */
    protected Browser signUp(String email) throws Exception {
        return signUp(email, allConsents());
    }

    protected Browser signUp(String email, String consents) throws Exception {
        Browser browser = browser();
        verifyEmail(browser, email, "SIGNUP");
        browser.post("/api/auth/signup", signupJson(email, consents))
                .andExpect(status().isCreated());
        return browser;
    }

    protected Browser logIn(String email, String password, boolean rememberMe) throws Exception {
        Browser browser = browser();
        browser.post("/api/auth/login", """
                {"email": "%s", "password": "%s", "rememberMe": %s}""".formatted(email, password, rememberMe))
                .andExpect(status().isOk());
        return browser;
    }

    /**
     * 로그인한 브라우저의 회원 id.
     */
    protected Long userId(Browser browser) throws Exception {
        String me = browser.get("/api/me").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(me, "$.id")).longValue();
    }

    protected void advance(Duration duration) {
        clock.advance(duration);
    }

    protected int rememberMeTokens(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM persistent_logins WHERE username = ?", Integer.class, email);
    }
}
