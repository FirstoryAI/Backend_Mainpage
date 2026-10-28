package com.firstory.domain.generation.entity;

import com.firstory.domain.story.entity.Story;
import com.firstory.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 동화 버전. 지우지 않는다. parentVersion으로 나무 구조를 이룬다.
 * (v1 → v2를 만든 뒤 v1로 돌아가 v3를 만들면 v3.parentVersion = v1, v2는 그대로 남음)
 */
// TODO(PM): 확정 후 사용하지 않는 버전 보관 정책 미정
@Getter
@Entity
@Table(name = "story_versions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"story_id", "version_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false, unique = true)
    private GenerationRun run;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_version_id")
    private StoryVersion parentVersion;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "checkpoint_id", length = 100)
    private String checkpointId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> design;

    @Column(name = "character_sheet_key")
    private String characterSheetKey;

    // 제목이 들어간 정사각형 표지. 없으면(이전 버전, 표지 실패) 캐릭터 시트를 표지로 쓴다
    @Column(name = "cover_key")
    private String coverKey;

    private StoryVersion(Story story, GenerationRun run, int versionNo, StoryVersion parentVersion, String title,
                         String checkpointId, Map<String, Object> design, String characterSheetKey,
                         String coverKey) {
        this.story = Objects.requireNonNull(story);
        this.run = Objects.requireNonNull(run);
        this.versionNo = versionNo;
        this.parentVersion = parentVersion;
        this.title = Objects.requireNonNull(title);
        this.checkpointId = checkpointId;
        this.design = design == null ? null : new LinkedHashMap<>(design);
        this.characterSheetKey = characterSheetKey;
        this.coverKey = coverKey;
    }

    public static StoryVersion create(Story story, GenerationRun run, int versionNo, StoryVersion parentVersion,
                                      String title, String checkpointId, Map<String, Object> design,
                                      String characterSheetKey, String coverKey) {
        return new StoryVersion(story, run, versionNo, parentVersion, title, checkpointId, design,
                characterSheetKey, coverKey);
    }

    public boolean belongsTo(Story other) {
        if (other == null) {
            return false;
        }
        if (story == other) {
            return true;
        }
        // 프록시일 수 있으므로 id로 비교한다 (getId는 프록시를 초기화하지 않음)
        Long storyId = story.getId();
        return storyId != null && storyId.equals(other.getId());
    }
}
