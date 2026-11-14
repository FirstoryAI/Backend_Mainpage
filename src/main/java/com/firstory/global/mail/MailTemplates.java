package com.firstory.global.mail;

import com.firstory.global.config.FirstoryProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTML 메일 (resources/mail/*.html). {{assetBase}}는 그림 주소 앞부분, {{appUrl}}은 프론트 주소, 그 밖의 {{이름}}은 values로 채운다.
 * 그림 파일은 resources/static/mail/에 있고 로그인 없이 /mail/*.png로 내려간다 (SecurityConfig).
 * 디자인은 홈 화면을 따른다. 만든 스크립트와 미리보기는 저장소 밖 mail-preview/build.py.
 */
@Component
public class MailTemplates {

    public static final String VERIFY_SIGNUP = "verify-signup";
    public static final String VERIFY_PASSWORD_RESET = "verify-password-reset";
    public static final String STORY_STARTED = "story-started";
    public static final String STORY_COMPLETED = "story-completed";
    public static final String STORY_FAILED = "story-failed";

    private final String assetBase;
    private final String appUrl;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Autowired
    public MailTemplates(FirstoryProperties properties) {
        this(properties.mail().assetBaseUrl(), properties.mail().appUrl());
    }

    public MailTemplates(String assetBaseUrl, String appUrl) {
        this.assetBase = trimSlash(assetBaseUrl);
        this.appUrl = trimSlash(appUrl);
    }

    /**
     * @return 채운 HTML. 그림·앱 주소 설정이 없으면 null (글자 메일만 보낸다)
     */
    public String html(String name, Map<String, String> values) {
        if (assetBase == null || appUrl == null) {
            return null;
        }
        String html = cache.computeIfAbsent(name, MailTemplates::load)
                .replace("{{assetBase}}", assetBase)
                .replace("{{appUrl}}", appUrl);
        for (Map.Entry<String, String> value : values.entrySet()) {
            html = html.replace("{{" + value.getKey() + "}}", value.getValue());
        }
        if (html.contains("{{")) {
            throw new IllegalStateException("메일 템플릿에 채우지 않은 칸이 있어요: " + name);
        }
        return html;
    }

    private static String load(String name) {
        try {
            return new ClassPathResource("mail/" + name + ".html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("메일 템플릿을 읽지 못했어요: " + name, e);
        }
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
