package com.firstory.support;

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
 * 실제 Security 필터, Spring Session JDBC, Flyway V1·V2가 모두 적용된 상태에서 도는 통합 테스트의 기반.
 * 테스트마다 다른 이메일과 번호를 써서 서로 영향을 주지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestBeans.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "firstory123";

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
        RecordingSmsSender recordingSmsSender() {
            return new RecordingSmsSender();
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected RecordingSmsSender sms;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetClock() {
        clock.setInstant(systemNow());
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

    protected static String newPhone() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "010-%04d-%04d".formatted(random.nextInt(10_000), random.nextInt(10_000));
    }

    protected void sendCode(Browser browser, String phone, String purpose) throws Exception {
        browser.post("/api/auth/phone/send-code", """
                {"phone": "%s", "purpose": "%s"}""".formatted(phone, purpose))
                .andExpect(status().isOk());
    }

    protected void verifyPhone(Browser browser, String phone, String purpose) throws Exception {
        sendCode(browser, phone, purpose);
        browser.post("/api/auth/phone/verify", """
                {"phone": "%s", "code": "%s"}""".formatted(phone, sms.lastCode(phone)))
                .andExpect(status().isNoContent());
    }

    protected static String signupJson(String email, String phone, String consents) {
        return """
                {"email": "%s", "password": "%s", "phone": "%s", "consents": %s, "termsVersion": "2026-10-01"}"""
                .formatted(email, PASSWORD, phone, consents);
    }

    protected static String allConsents() {
        return """
                {"GUARDIAN": true, "TERMS": true, "PRIVACY": true, "SMS_NOTIFY": false}""";
    }

    /**
     * 문자 인증부터 가입까지 마친 브라우저. 가입 직후 로그인된 상태다.
     */
    protected Browser signUp(String email, String phone) throws Exception {
        Browser browser = browser();
        verifyPhone(browser, phone, "SIGNUP");
        browser.post("/api/auth/signup", signupJson(email, phone, allConsents()))
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
