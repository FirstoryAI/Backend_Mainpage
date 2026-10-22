package com.firstory.domain.reading.entity;

import com.firstory.domain.guide.entity.GuidePrompt;
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

import java.util.Objects;

/**
 * 대화 질문에서 누른 버튼 기록 (North Star 집계용).
 */
@Getter
@Entity
@Table(name = "dialogue_actions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "prompt_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DialogueAction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ReadingSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prompt_id", nullable = false)
    private GuidePrompt prompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DialogueActionType action;

    private DialogueAction(ReadingSession session, GuidePrompt prompt, DialogueActionType action) {
        this.session = Objects.requireNonNull(session);
        this.prompt = Objects.requireNonNull(prompt);
        this.action = Objects.requireNonNull(action);
    }

    public static DialogueAction record(ReadingSession session, GuidePrompt prompt, DialogueActionType action) {
        return new DialogueAction(session, prompt, action);
    }

    /**
     * 같은 세션, 같은 질문에 다시 누르면 마지막 값으로 바꾼다 (잘못 누른 것을 고칠 수 있게).
     */
    public void change(DialogueActionType action) {
        this.action = Objects.requireNonNull(action);
    }
}
