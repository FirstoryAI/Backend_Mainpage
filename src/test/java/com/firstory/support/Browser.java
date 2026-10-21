package com.firstory.support;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 기기 하나(브라우저)를 흉내 낸다. 응답의 쿠키를 기억했다가 다음 요청에 붙이고,
 * 상태를 바꾸는 요청에는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 돌려보낸다.
 */
public class Browser {

    public static final String SESSION = "FSESSION";
    public static final String REMEMBER_ME = "FREMEMBER";
    public static final String XSRF = "XSRF-TOKEN";

    private final MockMvc mockMvc;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();

    public Browser(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public Browser fetchCsrf() throws Exception {
        get("/api/auth/csrf");
        return this;
    }

    public ResultActions get(String url) throws Exception {
        return perform(MockMvcRequestBuilders.get(url));
    }

    public ResultActions post(String url, String json) throws Exception {
        return perform(withCsrf(withBody(MockMvcRequestBuilders.post(url), json)));
    }

    public ResultActions put(String url, String json) throws Exception {
        return perform(withCsrf(withBody(MockMvcRequestBuilders.put(url), json)));
    }

    public ResultActions postWithoutCsrf(String url, String json) throws Exception {
        return perform(withBody(MockMvcRequestBuilders.post(url), json));
    }

    public Optional<Cookie> cookie(String name) {
        return Optional.ofNullable(cookies.get(name));
    }

    public boolean hasCookie(String name) {
        return cookies.containsKey(name);
    }

    /**
     * 다른 브라우저의 쿠키 하나만 가져온다 (세션이 끝난 뒤 remember-me로 다시 오는 상황).
     */
    public Browser withCookie(Cookie cookie) {
        cookies.put(cookie.getName(), cookie);
        return this;
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, String json) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json == null ? "" : json);
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
        cookie(XSRF).ifPresent(token -> builder.header("X-XSRF-TOKEN", token.getValue()));
        return builder;
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        if (!cookies.isEmpty()) {
            builder.cookie(cookies.values().toArray(Cookie[]::new));
        }
        ResultActions result = mockMvc.perform(builder);
        remember(result.andReturn());
        return result;
    }

    // Set-Cookie 순서대로 반영한다. Max-Age=0이거나 값이 비면 지운다
    private void remember(MvcResult result) {
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0 || cookie.getValue() == null || cookie.getValue().isEmpty()) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie);
            }
        }
    }
}
