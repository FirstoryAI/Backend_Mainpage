package com.firstory.domain.sample.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 홈 샘플 카드와 미리 만들어 둔 동화의 연결. 환경 변수 SAMPLE_STORIES에 "카드id:동화id"를 쉼표로 적는다.
 * 예: SAMPLE_STORIES=friend:41,emotion:42. 비어 있으면 모든 카드가 SAMPLE_NOT_FOUND다.
 * 형식이 틀리면 기동하지 않는다 (잘못 적은 것을 배포 뒤에야 알지 않게).
 *
 * @param stories "카드id:동화id" 목록
 */
@ConfigurationProperties("firstory.samples")
public record SampleStoryProperties(List<String> stories) {

    private static final Pattern ENTRY = Pattern.compile("([A-Za-z][A-Za-z0-9_-]{0,49}):(\\d{1,18})");

    public SampleStoryProperties {
        stories = stories == null ? List.of()
                : stories.stream().map(String::trim).filter(entry -> !entry.isEmpty()).toList();
        storyIds(stories);
    }

    /**
     * @return 카드 id → 동화 id. 적은 순서 그대로
     */
    public Map<String, Long> storyIds() {
        return storyIds(stories);
    }

    private static Map<String, Long> storyIds(List<String> entries) {
        Map<String, Long> ids = new LinkedHashMap<>();
        for (String entry : entries) {
            var matcher = ENTRY.matcher(entry);
            if (!matcher.matches()) {
                throw new IllegalStateException("SAMPLE_STORIES는 \"카드id:동화id\"를 쉼표로 적어요: " + entry);
            }
            if (ids.put(matcher.group(1), Long.parseLong(matcher.group(2))) != null) {
                throw new IllegalStateException("SAMPLE_STORIES에 같은 카드가 두 번 있어요: " + matcher.group(1));
            }
        }
        return Map.copyOf(ids);
    }
}
