package com.firstory.domain.generation.entity;

import com.firstory.domain.story.entity.Story;
import com.firstory.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Entity
@Table(name = "generation_runs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"story_id", "run_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GenerationRun extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(name = "run_no", nullable = false)
    private int runNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private RunTrigger triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RegenerateScope scope;

    // 다시 만들기의 기준 버전. 처음 만들기면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_version_id")
    private StoryVersion baseVersion;

    @Column(columnDefinition = "text")
    private String feedback;

    // 그림만 다시(IMAGES) 그릴 쪽 번호. 비어 있으면 전체
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_pages", nullable = false, columnDefinition = "jsonb")
    private List<Integer> targetPages = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    // GEN-01 진행 구간 표시용
    @Column(name = "current_node", length = 50)
    private String currentNode;

    @Column(name = "langsmith_trace_id", length = 64)
    private String langsmithTraceId;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "image_count", nullable = false)
    private int imageCount;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    private GenerationRun(Story story, int runNo, RunTrigger triggerType, RegenerateScope scope,
                          StoryVersion baseVersion, List<Integer> targetPages, String feedback,
                          String langsmithTraceId, Instant now) {
        this.story = Objects.requireNonNull(story);
        this.runNo = runNo;
        this.triggerType = Objects.requireNonNull(triggerType);
        this.scope = Objects.requireNonNull(scope);
        this.baseVersion = baseVersion;
        this.targetPages = targetPages == null ? new ArrayList<>() : new ArrayList<>(targetPages);
        this.feedback = feedback;
        this.langsmithTraceId = langsmithTraceId;
        this.status = RunStatus.RUNNING;
        this.startedAt = Objects.requireNonNull(now);
    }

    /**
     * @param targetPages 그림만 다시(IMAGES)일 때 다시 그릴 쪽. 다른 범위나 전체면 빈 목록
     */
    public static GenerationRun start(Story story, int runNo, RunTrigger trigger, RegenerateScope scope,
                                      StoryVersion baseVersion, List<Integer> targetPages, String feedback,
                                      String langsmithTraceId, Instant now) {
        return new GenerationRun(story, runNo, trigger, scope, baseVersion, targetPages, feedback,
                langsmithTraceId, now);
    }

    public boolean isRunning() {
        return status == RunStatus.RUNNING;
    }

    public void progress(String node) {
        this.currentNode = node;
    }

    public void succeed(int inputTokens, int outputTokens, int imageCount, Instant now) {
        this.status = RunStatus.SUCCEEDED;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.imageCount = imageCount;
        this.finishedAt = now;
    }

    public void fail(String errorCode, Instant now) {
        this.status = RunStatus.FAILED;
        this.errorCode = errorCode;
        this.finishedAt = now;
    }
}
