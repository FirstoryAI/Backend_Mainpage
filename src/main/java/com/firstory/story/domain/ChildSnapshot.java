package com.firstory.story.domain;

import com.firstory.child.domain.Child;
import com.firstory.child.domain.Gender;
import com.firstory.child.domain.Interests;
import com.firstory.child.domain.Trait;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

/**
 * 동화를 만들 때 AI 서비스로 보내는 아이 정보. story_inputs.child_snapshot(jsonb)에 저장한다.
 * 아이 호칭(displayName)은 절대 넣지 않는다.
 */
public record ChildSnapshot(int ageMonths, Gender gender, List<Trait> traits, List<String> interests) {

    public ChildSnapshot {
        traits = traits == null ? List.of() : List.copyOf(traits);
        interests = interests == null ? List.of() : List.copyOf(interests);
    }

    public static ChildSnapshot of(Child child, LocalDate today) {
        return new ChildSnapshot(child.ageMonthsAt(today), child.getGender(),
                sorted(child.getTraits()), child.getInterests());
    }

    public static ChildSnapshot of(YearMonth birthYm, Gender gender, Collection<Trait> traits,
                                   List<String> interests, LocalDate today) {
        int ageMonths = (int) ChronoUnit.MONTHS.between(birthYm, YearMonth.from(today));
        return new ChildSnapshot(ageMonths, gender, sorted(traits), Interests.normalize(interests));
    }

    private static List<Trait> sorted(Collection<Trait> traits) {
        return traits == null ? List.of() : traits.stream().distinct().sorted().toList();
    }
}
