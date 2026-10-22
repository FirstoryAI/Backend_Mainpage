package com.firstory.domain.story.entity;

import com.firstory.global.entity.BaseTimeEntity;
import com.firstory.global.entity.CustomText;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 동화 만들기 입력. stories와 PK를 공유하는 1:1.
 * 필드는 AI 입력 형식(StoryInputContext)의 situation, moment, parent와 같은 단위로 나눈다. 변환은 AiContextMapper가 한다.
 */
@Getter
@Entity
@Table(name = "story_inputs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryInput extends BaseTimeEntity {

    public static final int DESCRIPTION_MAX_LENGTH = 60;
    public static final int CUSTOM_CATEGORY_MAX_LENGTH = 30;
    public static final int CUSTOM_REACTION_MAX_LENGTH = 30;

    @Id
    @Column(name = "story_id")
    private Long storyId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;

    @Enumerated(EnumType.STRING)
    @Column(name = "situation_timing", length = 20)
    private SituationTiming situationTiming;

    @Enumerated(EnumType.STRING)
    @Column(name = "situation_category", length = 50)
    private SituationCategory situationCategory;

    // situationCategory가 OTHER일 때만 값이 있다
    @Column(name = "situation_custom_category", length = 30)
    private String situationCustomCategory;

    @Column(columnDefinition = "text")
    private String description;

    // MomentReaction 선언 순서. 앞으로 겪을 일(UPCOMING)이면 비어 있다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "moment_reactions", nullable = false, columnDefinition = "jsonb")
    private List<MomentReaction> momentReactions = new ArrayList<>();

    // momentReactions에 OTHER가 있을 때만 값이 있다
    @Column(name = "moment_custom_reaction", length = 30)
    private String momentCustomReaction;

    // Emotion 선언 순서. 앞으로 겪을 일(UPCOMING)이면 비어 있다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Emotion> emotions = new ArrayList<>();

    // parentResponse, parentConcern은 주로 인터뷰 결과로 채운다
    @Column(name = "parent_response", columnDefinition = "text")
    private String parentResponse;

    @Column(name = "parent_concern", columnDefinition = "text")
    private String parentConcern;

    @Column(name = "parent_message", columnDefinition = "text")
    private String parentMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_direction", length = 50)
    private MessageDirection messageDirection;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Tone tone;

    // 민감 주제(SENSITIVE)가 감지되면 true. 생성 전에 분위기(tone)를 골라야 한다
    @Column(name = "tone_required", nullable = false)
    private boolean toneRequired;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "child_snapshot", columnDefinition = "jsonb")
    private ChildSnapshot childSnapshot;

    @Column(name = "interview_summary", columnDefinition = "text")
    private String interviewSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_end_reason", length = 20)
    private InterviewEndReason interviewEndReason;

    // 인터뷰가 끝난 뒤 AI가 확정한 context 전체. AI가 채운 parent 값의 원본
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "final_context", columnDefinition = "jsonb")
    private Map<String, Object> finalContext;

    private StoryInput(Story story) {
        this.story = Objects.requireNonNull(story);
    }

    public static StoryInput begin(Story story) {
        return new StoryInput(story);
    }

    /**
     * 상황과 그 순간 아이의 반응, 감정 (CRT-01, CRT-02).
     * 기타(OTHER)를 고르면 직접 입력이 필수이고, 고르지 않았으면 직접 입력은 버린다.
     * 반응과 감정은 있었던 일(PAST)에서만 고를 수 있다. 목록은 중복을 지우고 선언 순서로 정렬한다.
     */
    public void writeSituation(SituationTiming timing, SituationCategory category, String customCategory,
                               String description, Collection<MomentReaction> reactions, String customReaction,
                               Collection<Emotion> emotions) {
        if (timing == null || category == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        String strippedDescription = CustomText.strip(description);
        if (strippedDescription != null
                && strippedDescription.codePointCount(0, strippedDescription.length()) > DESCRIPTION_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.DESCRIPTION_TOO_LONG);
        }
        List<MomentReaction> reactionList = sorted(MomentReaction.class, reactions);
        List<Emotion> emotionList = sorted(Emotion.class, emotions);
        if (timing == SituationTiming.UPCOMING && (!reactionList.isEmpty() || !emotionList.isEmpty())) {
            throw new BusinessException(ErrorCode.MOMENT_ONLY_FOR_PAST);
        }
        this.situationCustomCategory = CustomText.resolve(category == SituationCategory.OTHER, customCategory,
                CUSTOM_CATEGORY_MAX_LENGTH);
        this.momentCustomReaction = CustomText.resolve(reactionList.contains(MomentReaction.OTHER), customReaction,
                CUSTOM_REACTION_MAX_LENGTH);
        this.situationTiming = timing;
        this.situationCategory = category;
        this.description = strippedDescription;
        this.momentReactions = new ArrayList<>(reactionList);
        this.emotions = new ArrayList<>(emotionList);
    }

    public void writeChild(ChildSnapshot childSnapshot) {
        this.childSnapshot = Objects.requireNonNull(childSnapshot);
    }

    /**
     * 부모의 대응, 걱정, 전하고 싶은 말과 방향. 값이 없으면 null로 둔다.
     */
    public void writeParent(String response, String concern, String message, MessageDirection direction) {
        this.parentResponse = response;
        this.parentConcern = concern;
        this.parentMessage = message;
        this.messageDirection = direction;
    }

    public void chooseTone(Tone tone) {
        this.tone = Objects.requireNonNull(tone);
    }

    /**
     * 민감 주제(SENSITIVE)가 감지됐다. SENSITIVE는 safety_flags에 기록하지 않으므로 여기에만 남긴다.
     */
    public void markToneRequired() {
        this.toneRequired = true;
    }

    public void finishInterview(String interviewSummary, InterviewEndReason reason, Map<String, Object> finalContext) {
        this.interviewSummary = interviewSummary;
        this.interviewEndReason = Objects.requireNonNull(reason);
        this.finalContext = finalContext == null ? null : new LinkedHashMap<>(finalContext);
    }

    private static <E extends Enum<E>> List<E> sorted(Class<E> type, Collection<E> values) {
        Set<E> set = EnumSet.noneOf(type);
        if (values != null) {
            values.stream().filter(Objects::nonNull).forEach(set::add);
        }
        return List.copyOf(set);
    }
}
