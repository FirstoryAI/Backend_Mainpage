package com.firstory.domain.guide.entity;

import com.firstory.domain.generation.entity.StoryVersion;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 버전마다 하나씩 있는 대화 가이드.
 */
@Getter
@Entity
@Table(name = "reading_guides")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingGuide extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, unique = true)
    private StoryVersion version;

    @Column(name = "before_intent", columnDefinition = "text")
    private String beforeIntent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_tips", nullable = false, columnDefinition = "jsonb")
    private List<String> beforeTips = new ArrayList<>();

    @Column(name = "bridge_question", columnDefinition = "text")
    private String bridgeQuestion;

    @Column(name = "if_undecided", columnDefinition = "text")
    private String ifUndecided;

    private ReadingGuide(StoryVersion version, String beforeIntent, List<String> beforeTips,
                         String bridgeQuestion, String ifUndecided) {
        this.version = Objects.requireNonNull(version);
        this.beforeIntent = beforeIntent;
        this.beforeTips = beforeTips == null ? new ArrayList<>() : new ArrayList<>(beforeTips);
        this.bridgeQuestion = bridgeQuestion;
        this.ifUndecided = ifUndecided;
    }

    public static ReadingGuide create(StoryVersion version, String beforeIntent, List<String> beforeTips,
                                      String bridgeQuestion, String ifUndecided) {
        return new ReadingGuide(version, beforeIntent, beforeTips, bridgeQuestion, ifUndecided);
    }
}
