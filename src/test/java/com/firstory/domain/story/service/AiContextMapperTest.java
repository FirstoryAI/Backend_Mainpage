package com.firstory.domain.story.service;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.InterestCode;
import com.firstory.domain.child.entity.Interests;
import com.firstory.domain.child.entity.Trait;
import com.firstory.domain.story.entity.ChildSnapshot;
import com.firstory.domain.story.entity.Emotion;
import com.firstory.domain.story.entity.MessageDirection;
import com.firstory.domain.story.entity.MomentReaction;
import com.firstory.domain.story.entity.SituationCategory;
import com.firstory.domain.story.entity.SituationTiming;
import com.firstory.domain.story.entity.StoryInput;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static com.firstory.support.Fixtures.story;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

class AiContextMapperTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void AI_입력_형식_예시와_필드_단위로_같다() throws Exception {
        StoryInput input = StoryInput.begin(story(user()));
        input.writeSituation(SituationTiming.PAST, SituationCategory.FRIEND_CONFLICT, null, "최대 60자",
                List.of(MomentReaction.CRIED, MomentReaction.OTHER), "방에 숨었어요", List.of(Emotion.SAD, Emotion.SCARED));
        input.writeChild(new ChildSnapshot(63, Gender.FEMALE, List.of(Trait.SHY, Trait.CAUTIOUS), null,
                List.of(InterestCode.DINOSAUR), List.of("파란색", "기차")));
        input.writeParent(null, null, "친구에게 먼저 말을 걸어보는 용기를 주고 싶어요", MessageDirection.PERSPECTIVE_TAKING);

        JsonNode actual = JSON.valueToTree(AiContextMapper.toContext(input));

        JsonNode expected;
        try (InputStream in = getClass().getResourceAsStream("/ai/story-input-context.json")) {
            expected = JSON.readTree(in);
        }
        // 성향은 집합이라 순서에 뜻이 없다. 우리는 Trait 선언 순서(cautious, shy)로, 예시는 shy, cautious로 적혀 있어 정렬해서 비교한다
        sortTraits(expected);
        sortTraits(actual);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void 직접_입력한_좋아하는_것이_없으면_other를_넣지_않는다() {
        StoryInput input = pastInput();
        input.writeChild(new ChildSnapshot(40, Gender.MALE, List.of(), null,
                List.of(InterestCode.SPACE, InterestCode.VEHICLE), List.of()));

        Map<String, Object> child = section(AiContextMapper.toContext(input), "child");

        assertThat(child.get("interests")).isEqualTo(List.of("space", "vehicle"));
        assertThat(child).containsEntry("custom_interest", null);
    }

    @Test
    void 성향_OTHER의_직접_입력을_보낸다() {
        StoryInput input = pastInput();
        input.writeChild(new ChildSnapshot(40, Gender.UNSPECIFIED, List.of(Trait.SHY, Trait.OTHER), "겁이 많아요",
                List.of(), List.of()));

        Map<String, Object> child = section(AiContextMapper.toContext(input), "child");

        assertThat(child.get("gender")).isEqualTo("unspecified");
        assertThat(child.get("traits")).isEqualTo(List.of("shy", "other"));
        assertThat(child).containsEntry("custom_trait", "겁이 많아요");
        assertThat(child.get("interests")).isEqualTo(List.of());
    }

    @Test
    void 앞으로_겪을_일이면_moment는_비어_있다() {
        StoryInput input = StoryInput.begin(story(user()));
        input.writeSituation(SituationTiming.UPCOMING, SituationCategory.OTHER, "동생이 생겨요", "곧 동생이 태어나요",
                null, null, null);
        input.writeChild(new ChildSnapshot(50, Gender.FEMALE, List.of(), null, List.of(), List.of()));

        Map<String, Object> context = AiContextMapper.toContext(input);

        Map<String, Object> moment = section(context, "moment");
        assertThat(moment.keySet()).containsExactly("reactions", "custom_reaction", "emotions");
        assertThat(moment)
                .containsEntry("reactions", List.of())
                .containsEntry("custom_reaction", null)
                .containsEntry("emotions", List.of());
        assertThat(section(context, "situation"))
                .containsEntry("timing", "upcoming")
                .containsEntry("category", "other")
                .containsEntry("custom_category", "동생이 생겨요");
    }

    @Test
    void parent는_값이_없어도_키를_넣고_null() {
        StoryInput input = pastInput();
        input.writeChild(new ChildSnapshot(50, Gender.FEMALE, List.of(), null, List.of(), List.of()));

        Map<String, Object> parent = section(AiContextMapper.toContext(input), "parent");

        assertThat(parent.keySet()).containsExactly("response", "concern", "message", "message_direction");
        assertThat(parent.values()).containsOnlyNulls();
    }

    @Test
    void 결과_어디에도_아이_호칭이_없다() {
        Child child = Child.save(user(), "우리첫째", YearMonth.of(2021, 3), Gender.FEMALE, Set.of(Trait.OTHER),
                "겁이 많아요",
                new Interests(List.of(InterestCode.DINOSAUR), List.of("기차")));
        StoryInput input = pastInput();
        input.writeChild(ChildSnapshot.of(child, LocalDate.of(2026, 10, 21)));

        String json = JSON.writeValueAsString(AiContextMapper.toContext(input));

        assertThat(json).doesNotContain("우리첫째").doesNotContain("display").doesNotContain("name");
    }

    private static StoryInput pastInput() {
        StoryInput input = StoryInput.begin(story(user()));
        input.writeSituation(SituationTiming.PAST, SituationCategory.FEAR, null, "설명", null, null, null);
        return input;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> context, String key) {
        return (Map<String, Object>) context.get(key);
    }

    private static void sortTraits(JsonNode context) {
        ObjectNode child = (ObjectNode) context.get("child");
        List<String> traits = StreamSupport.stream(child.get("traits").spliterator(), false)
                .map(JsonNode::asString).sorted().toList();
        ArrayNode sorted = child.putArray("traits");
        traits.forEach(sorted::add);
    }
}
