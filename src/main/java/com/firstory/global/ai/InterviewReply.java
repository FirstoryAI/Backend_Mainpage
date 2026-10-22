package com.firstory.global.ai;

import com.firstory.domain.story.entity.InterviewEndReason;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 인터뷰 응답. QUESTION이면 turn, question, contextUpdate가, CONFIRM이면 summary, endReason, finalContext가 있다.
 *
 * @param finalContext 인터뷰가 채운 parent 값까지 포함한 최종 StoryInputContext
 */
public record InterviewReply(Type type, Integer turn, String question, Map<String, Object> contextUpdate,
                             String summary, InterviewEndReason endReason, Map<String, Object> finalContext) {

    public enum Type {
        QUESTION,
        CONFIRM
    }

    public InterviewReply {
        Objects.requireNonNull(type);
        // AI 응답에는 null 값이 있어 Map.copyOf를 쓰지 않는다
        contextUpdate = copy(contextUpdate);
        finalContext = copy(finalContext);
    }

    public static InterviewReply question(int turn, String question, Map<String, Object> contextUpdate) {
        return new InterviewReply(Type.QUESTION, turn, Objects.requireNonNull(question), contextUpdate,
                null, null, null);
    }

    public static InterviewReply confirm(String summary, InterviewEndReason endReason,
                                         Map<String, Object> finalContext) {
        return new InterviewReply(Type.CONFIRM, null, null, null, summary, Objects.requireNonNull(endReason),
                finalContext);
    }

    public boolean isConfirm() {
        return type == Type.CONFIRM;
    }

    private static Map<String, Object> copy(Map<String, Object> map) {
        return map == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    // 요약과 context에 부모 입력이 들어 있어 로그에 남기지 않는다
    @Override
    public String toString() {
        return "InterviewReply[type=" + type + ", turn=" + turn + "]";
    }
}
