package com.firstory.domain.story.repository;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.InterestCode;
import com.firstory.domain.child.entity.Interests;
import com.firstory.domain.child.entity.Trait;
import com.firstory.domain.child.repository.ChildRepository;
import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.generation.repository.GenerationRunRepository;
import com.firstory.domain.generation.repository.StoryVersionRepository;
import com.firstory.domain.story.entity.ChildSnapshot;
import com.firstory.domain.story.entity.Emotion;
import com.firstory.domain.story.entity.InterviewEndReason;
import com.firstory.domain.story.entity.MessageDirection;
import com.firstory.domain.story.entity.MomentReaction;
import com.firstory.domain.story.entity.SituationCategory;
import com.firstory.domain.story.entity.SituationTiming;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryInput;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.support.RepositoryTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.firstory.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class StoryRepositoryTest extends RepositoryTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    ChildRepository childRepository;

    @Autowired
    StoryRepository storyRepository;

    @Autowired
    StoryInputRepository storyInputRepository;

    @Autowired
    GenerationRunRepository runRepository;

    @Autowired
    StoryVersionRepository versionRepository;

    @Autowired
    EntityManager em;

    @Test
    void 아이_스냅샷이_jsonb로_왕복된다() {
        User user = userRepository.save(user());
        Story story = storyRepository.save(story(user));
        StoryInput input = StoryInput.begin(story);
        ChildSnapshot snapshot = ChildSnapshot.of(YearMonth.of(2021, 3), Gender.MALE,
                Set.of(Trait.STUBBORN, Trait.OTHER), "겁이 많아요",
                new Interests(List.of(InterestCode.VEHICLE), List.of("자동차")), LocalDate.of(2026, 10, 21));
        input.writeChild(snapshot);
        storyInputRepository.save(input);
        em.flush();
        em.clear();

        StoryInput found = storyInputRepository.findById(story.getId()).orElseThrow();

        assertThat(found.getChildSnapshot()).isEqualTo(snapshot);
    }

    @Test
    void 만들기_입력의_jsonb_값이_그대로_돌아온다() {
        User user = userRepository.save(user());
        Story story = storyRepository.save(story(user));
        StoryInput input = StoryInput.begin(story);
        input.writeSituation(SituationTiming.PAST, SituationCategory.OTHER, "동생이 생겼어요", "설명",
                List.of(MomentReaction.CRIED, MomentReaction.OTHER), "방에 숨었어요", List.of(Emotion.SAD, Emotion.SCARED));
        input.writeParent(null, "걱정", "용기를 주고 싶어요", MessageDirection.PERSPECTIVE_TAKING);
        Map<String, Object> finalContext = Map.of(
                "parent", Map.of("concern", "걱정", "message_direction", "perspective_taking"),
                "moment", Map.of("reactions", List.of("cried", "other")));
        input.finishInterview("요약", InterviewEndReason.SUFFICIENT, finalContext);
        storyInputRepository.save(input);
        em.flush();
        em.clear();

        StoryInput found = storyInputRepository.findById(story.getId()).orElseThrow();

        assertThat(found.getSituationCategory()).isEqualTo(SituationCategory.OTHER);
        assertThat(found.getSituationCustomCategory()).isEqualTo("동생이 생겼어요");
        assertThat(found.getMomentReactions()).containsExactly(MomentReaction.CRIED, MomentReaction.OTHER);
        assertThat(found.getMomentCustomReaction()).isEqualTo("방에 숨었어요");
        assertThat(found.getEmotions()).containsExactly(Emotion.SAD, Emotion.SCARED);
        assertThat(found.getMessageDirection()).isEqualTo(MessageDirection.PERSPECTIVE_TAKING);
        assertThat(found.getFinalContext()).isEqualTo(finalContext);
    }

    @Test
    void 버전을_저장하고_보여줄_버전을_바꾼다() {
        User user = userRepository.save(user());
        Story story = storyRepository.save(story(user));
        moveToReviewing(story);

        GenerationRun run1 = runRepository.save(run(story, 1));
        StoryVersion v1 = versionRepository.save(version(story, run1, 1, null));
        story.showVersion(v1);
        GenerationRun run2 = runRepository.save(run(story, 2));
        StoryVersion v2 = versionRepository.save(version(story, run2, 2, v1));
        story.showVersion(v2);
        story.showVersion(v1);
        em.flush();
        em.clear();

        Story found = storyRepository.findByIdAndUserId(story.getId(), user.getId()).orElseThrow();

        assertThat(found.getCurrentVersion().getId()).isEqualTo(v1.getId());
        assertThat(found.getTitle()).isEqualTo("제목 v1");
        assertThat(versionRepository.findAllByStoryIdOrderByVersionNoAsc(story.getId()))
                .extracting(StoryVersion::getVersionNo).containsExactly(1, 2);
        assertThat(versionRepository.findById(v2.getId()).orElseThrow().getDesign())
                .containsEntry("hero", "토끼");
    }

    @Test
    void 아이_연결을_끊으면_child가_null이_된다() {
        User user = userRepository.save(user());
        Child child = childRepository.save(child(user, "아이 A"));
        Story story = storyRepository.save(Story.start(user, child, "thread-detach"));
        em.flush();

        int updated = storyRepository.detachChild(child.getId());

        assertThat(updated).isEqualTo(1);
        assertThat(storyRepository.findById(story.getId()).orElseThrow().getChild()).isNull();
    }
}
