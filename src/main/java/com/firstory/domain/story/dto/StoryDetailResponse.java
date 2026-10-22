package com.firstory.domain.story.dto;

import com.firstory.domain.story.entity.SituationCategory;
import com.firstory.domain.story.entity.SituationTiming;
import com.firstory.domain.story.entity.StoryStatus;

import java.time.Instant;

/**
 * 동화 상세 (LIB-02). 차단된 동화도 상태 그대로 보여준다 (차단 화면 재진입용).
 *
 * @param situation 상황을 아직 입력하지 않았으면 null
 * @param summary   인터뷰 요약. 인터뷰 전이면 null
 * @param pageCount 버전이 있으면 쪽수, 없으면 null
 * @param readCount 끝까지 읽은 횟수 (완료된 읽기 세션 수)
 * @param lastReadAt 마지막으로 읽기를 시작한 시각. 읽은 적이 없으면 null
 * @param canRead   COMPLETED일 때만 true
 */
public record StoryDetailResponse(Long id, String title, StoryStatus status, String label, String coverUrl,
                                  String childDisplayName, Situation situation, String summary, Integer pageCount,
                                  Instant createdAt, Instant confirmedAt, long readCount, Instant lastReadAt,
                                  boolean canRead) {

    public record Situation(SituationCategory category, String categoryLabel, SituationTiming timing) {
    }

    // 인터뷰 요약에 부모 입력이 반영되어 있어 로그에 남기지 않는다
    @Override
    public String toString() {
        return "StoryDetailResponse[id=" + id + ", status=" + status + "]";
    }
}
