package com.firstory.domain.story.service;

import com.firstory.domain.story.entity.SituationCategory;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 지금 만들지 않는 주제 (#79, 이별·죽음 품질 문제). 설정 {@code firstory.story.blocked-topics} 한 곳에서 정한다.
 * <ul>
 *   <li>상황 칸(SituationCategory)이 막힌 주제면 저장·인터뷰 시작·만들기를 거절한다 (CATEGORY_UNAVAILABLE)</li>
 *   <li>AI 위기 신호 판단의 topic(같은 이름의 소문자)이 막힌 주제면 다시 쓰기 안내·결과 실패로 거른다</li>
 * </ul>
 */
@Component
public class BlockedTopics {

    private final Set<SituationCategory> blocked;

    @Autowired
    public BlockedTopics(FirstoryProperties properties) {
        this(parse(properties.story() == null ? null : properties.story().blockedTopics()));
    }

    private BlockedTopics(Set<SituationCategory> blocked) {
        this.blocked = blocked;
    }

    public static BlockedTopics of(Collection<SituationCategory> blocked) {
        return new BlockedTopics(blocked.isEmpty() ? EnumSet.noneOf(SituationCategory.class) : EnumSet.copyOf(blocked));
    }

    public boolean blocks(SituationCategory category) {
        return category != null && blocked.contains(category);
    }

    /**
     * @param topic AI 위기 신호 판단의 topic (예: separation). null이면 false
     */
    public boolean blocksAi(String topic) {
        return topic != null && blocked.stream().anyMatch(category -> category.aiValue().equals(topic));
    }

    /**
     * AI 인터뷰 답변 요청에 보내는 값 (blocked_topics).
     */
    public List<String> aiValues() {
        return blocked.stream().map(SituationCategory::aiValue).toList();
    }

    public void requireOpen(SituationCategory category) {
        if (blocks(category)) {
            throw new BusinessException(ErrorCode.CATEGORY_UNAVAILABLE);
        }
    }

    // 모르는 이름이면 기동하지 않는다 (설정 오타로 조용히 다시 열리지 않게)
    private static Set<SituationCategory> parse(List<String> names) {
        EnumSet<SituationCategory> result = EnumSet.noneOf(SituationCategory.class);
        if (names == null) {
            return result;
        }
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                result.add(SituationCategory.valueOf(name.strip().toUpperCase(Locale.ROOT)));
            }
        }
        return result;
    }
}
