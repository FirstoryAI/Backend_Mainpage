package com.firstory.domain.sample.service;

import com.firstory.domain.sample.config.SampleStoryProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 샘플 카드 id로 미리 만들어 둔 동화 id를 찾는다 (SampleStoryProperties).
 */
@Component
public class SampleCatalog {

    private final Map<String, Long> storyIds;

    public SampleCatalog(SampleStoryProperties properties) {
        this.storyIds = properties.storyIds();
    }

    public Optional<Long> storyId(String cardId) {
        return Optional.ofNullable(storyIds.get(cardId));
    }
}
