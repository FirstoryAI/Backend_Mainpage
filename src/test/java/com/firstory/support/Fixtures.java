package com.firstory.support;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.Trait;
import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.domain.generation.entity.RegenerateScope;
import com.firstory.domain.generation.entity.RunTrigger;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.user.entity.User;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Fixtures {

    public static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

    private Fixtures() {
    }

    public static User user(String email) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        return User.signUp(email, "$2a$10$hash", "enc-" + unique, (unique + unique).substring(0, 64), NOW);
    }

    public static User user() {
        return user(UUID.randomUUID() + "@example.com");
    }

    public static Child child(User user, String displayName) {
        return Child.save(user, displayName, YearMonth.of(2021, 3), Gender.FEMALE,
                Set.of(Trait.SHY, Trait.CURIOUS), List.of("공룡", "기차"));
    }

    public static Story story(User user) {
        return Story.start(user, null, UUID.randomUUID().toString());
    }

    /**
     * 생성 흐름을 따라 REVIEWING까지 보낸다.
     */
    public static void moveToReviewing(Story story) {
        for (StoryStatus next : List.of(StoryStatus.INTERVIEWING, StoryStatus.CONFIRMING, StoryStatus.REQUESTED,
                StoryStatus.GENERATING_TEXT, StoryStatus.GENERATING_IMAGES, StoryStatus.REVIEWING)) {
            story.changeStatus(next);
        }
    }

    public static GenerationRun run(Story story, int runNo) {
        RunTrigger trigger = runNo == 1 ? RunTrigger.INITIAL : RunTrigger.REGENERATE;
        return GenerationRun.start(story, runNo, trigger, RegenerateScope.ALL, null, null, null, NOW);
    }

    public static StoryVersion version(Story story, GenerationRun run, int versionNo, StoryVersion parent) {
        return StoryVersion.create(story, run, versionNo, parent, "제목 v" + versionNo, "cp-" + versionNo,
                Map.of("hero", "토끼"), null);
    }
}
