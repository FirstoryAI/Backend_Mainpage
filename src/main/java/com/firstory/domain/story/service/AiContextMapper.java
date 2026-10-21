package com.firstory.domain.story.service;

import com.firstory.domain.child.entity.Trait;
import com.firstory.domain.story.entity.ChildSnapshot;
import com.firstory.domain.story.entity.MomentReaction;
import com.firstory.domain.story.entity.SituationCategory;
import com.firstory.domain.story.entity.SituationTiming;
import com.firstory.domain.story.entity.StoryInput;
import com.firstory.global.entity.AiValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * StoryInput을 AI 입력 형식(StoryInputContext)으로 바꾼다. 순수 변환이라 Spring에 의존하지 않는다.
 *
 * <ul>
 *   <li>키는 AI와 같은 snake_case, enum 값은 aiValue()(소문자)</li>
 *   <li>직접 입력(custom_*)은 해당 OTHER가 있을 때만 값, 아니면 null</li>
 *   <li>child.interests: 고른 값 + 직접 입력이 있으면 "other". custom_interest는 직접 입력을 ", "로 이어 붙인 한 칸</li>
 *   <li>앞으로 겪을 일(UPCOMING)이면 moment는 빈 목록</li>
 *   <li>값이 없는 필드도 키는 넣고 null</li>
 *   <li>아이 호칭은 어떤 경우에도 넣지 않는다 (ChildSnapshot에도 없다)</li>
 * </ul>
 */
public final class AiContextMapper {

    static final String OTHER = "other";
    static final String CUSTOM_INTEREST_DELIMITER = ", ";

    private AiContextMapper() {
    }

    public static Map<String, Object> toContext(StoryInput input) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("situation", situation(input));
        context.put("child", child(input.getChildSnapshot()));
        context.put("moment", moment(input));
        context.put("parent", parent(input));
        return context;
    }

    private static Map<String, Object> situation(StoryInput input) {
        Map<String, Object> situation = new LinkedHashMap<>();
        situation.put("category", aiValue(input.getSituationCategory()));
        situation.put("custom_category", input.getSituationCategory() == SituationCategory.OTHER
                ? input.getSituationCustomCategory() : null);
        situation.put("timing", aiValue(input.getSituationTiming()));
        situation.put("description", input.getDescription());
        return situation;
    }

    private static Map<String, Object> child(ChildSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalStateException("아이 정보(childSnapshot)가 없어요");
        }
        List<String> interests = new ArrayList<>(aiValues(snapshot.interestCodes()));
        boolean hasCustomInterest = !snapshot.customInterests().isEmpty();
        if (hasCustomInterest) {
            interests.add(OTHER);
        }
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("age_months", snapshot.ageMonths());
        child.put("gender", aiValue(snapshot.gender()));
        child.put("traits", aiValues(snapshot.traits()));
        child.put("custom_trait", snapshot.traits().contains(Trait.OTHER) ? snapshot.customTrait() : null);
        child.put("interests", interests);
        child.put("custom_interest", hasCustomInterest
                ? String.join(CUSTOM_INTEREST_DELIMITER, snapshot.customInterests()) : null);
        return child;
    }

    private static Map<String, Object> moment(StoryInput input) {
        boolean upcoming = input.getSituationTiming() == SituationTiming.UPCOMING;
        Map<String, Object> moment = new LinkedHashMap<>();
        moment.put("reactions", upcoming ? List.of() : aiValues(input.getMomentReactions()));
        moment.put("custom_reaction", !upcoming && input.getMomentReactions().contains(MomentReaction.OTHER)
                ? input.getMomentCustomReaction() : null);
        moment.put("emotions", upcoming ? List.of() : aiValues(input.getEmotions()));
        return moment;
    }

    private static Map<String, Object> parent(StoryInput input) {
        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("response", input.getParentResponse());
        parent.put("concern", input.getParentConcern());
        parent.put("message", input.getParentMessage());
        parent.put("message_direction", aiValue(input.getMessageDirection()));
        return parent;
    }

    private static String aiValue(AiValue value) {
        return value == null ? null : value.aiValue();
    }

    private static List<String> aiValues(Collection<? extends AiValue> values) {
        return values == null ? List.of() : values.stream().map(AiValue::aiValue).toList();
    }
}
