package com.firstory.domain.reading.entity;

import com.firstory.domain.story.entity.Story;
import com.firstory.global.entity.BaseTimeEntity;
import com.firstory.global.entity.CustomText;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 함께 읽기 끝(RD-03) 피드백. 읽기 세션마다 하나, 세션의 동화와 함께 남긴다.
 */
@Getter
@Entity
@Table(name = "reading_feedbacks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingFeedback extends BaseTimeEntity {

    public static final int MAX_COMMENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private ReadingSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Enumerated(EnumType.STRING)
    @Column(name = "child_interest", nullable = false, length = 30)
    private ChildInterest childInterest;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_flow", nullable = false, length = 30)
    private ConversationFlow conversationFlow;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_helpfulness", nullable = false, length = 30)
    private ConversationHelpfulness conversationHelpfulness;

    // 비었으면 null
    @Column(length = MAX_COMMENT_LENGTH)
    private String comment;

    private ReadingFeedback(ReadingSession session, ChildInterest childInterest, ConversationFlow conversationFlow,
                            ConversationHelpfulness conversationHelpfulness, String comment) {
        this.session = Objects.requireNonNull(session);
        this.story = session.getStory();
        this.childInterest = Objects.requireNonNull(childInterest);
        this.conversationFlow = Objects.requireNonNull(conversationFlow);
        this.conversationHelpfulness = Objects.requireNonNull(conversationHelpfulness);
        this.comment = CustomText.strip(comment);
    }

    /**
     * @param comment 앞뒤 공백을 지우고 비었으면 null로 둔다
     */
    public static ReadingFeedback record(ReadingSession session, ChildInterest childInterest,
                                         ConversationFlow conversationFlow,
                                         ConversationHelpfulness conversationHelpfulness, String comment) {
        return new ReadingFeedback(session, childInterest, conversationFlow, conversationHelpfulness, comment);
    }
}
