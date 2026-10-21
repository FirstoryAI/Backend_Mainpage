package com.firstory.domain.story.repository;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.Trait;
import com.firstory.domain.child.repository.ChildRepository;
import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.generation.repository.GenerationRunRepository;
import com.firstory.domain.generation.repository.StoryVersionRepository;
import com.firstory.domain.story.entity.ChildSnapshot;
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
                Set.of(Trait.STUBBORN, Trait.SENSITIVE), List.of("자동차"), LocalDate.of(2026, 10, 21));
        input.writeChild(snapshot);
        storyInputRepository.save(input);
        em.flush();
        em.clear();

        StoryInput found = storyInputRepository.findById(story.getId()).orElseThrow();

        assertThat(found.getChildSnapshot()).isEqualTo(snapshot);
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
