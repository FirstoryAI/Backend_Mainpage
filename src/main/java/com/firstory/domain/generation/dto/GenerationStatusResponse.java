package com.firstory.domain.generation.dto;

import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.domain.generation.entity.GenerationStage;
import com.firstory.domain.generation.entity.RunStatus;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;

import java.time.Instant;

/**
 * 생성 진행 (GEN-01). DB에 저장된 값만 보여준다 (AI를 부르지 않는다).
 *
 * @param runNo 마지막 실행 번호. 실행이 없으면 null
 * @param stage 1 이야기 짜기, 2 글 쓰기, 3 그림 그리기, 4 책으로 묶기. 아직 시작 전이면 null
 * @param error 마지막 실행이 실패했으면 오류 코드 (TIMEOUT, OUTPUT_UNSAFE 등). 아니면 null
 */
public record GenerationStatusResponse(Long storyId, StoryStatus status, Integer runNo, Integer stage,
                                       String stageLabel, Instant startedAt, String error) {

    public static GenerationStatusResponse of(Story story, GenerationRun run) {
        if (run == null) {
            return new GenerationStatusResponse(story.getId(), story.getStatus(), null, null, null, null, null);
        }
        GenerationStage stage = GenerationStage.ofNode(run.getCurrentNode()).orElse(null);
        return new GenerationStatusResponse(story.getId(), story.getStatus(), run.getRunNo(),
                stage == null ? null : stage.getNumber(), stage == null ? null : stage.getLabel(),
                run.getStartedAt(), run.getStatus() == RunStatus.FAILED ? run.getErrorCode() : null);
    }
}
