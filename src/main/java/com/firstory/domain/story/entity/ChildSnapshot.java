package com.firstory.domain.story.entity;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.InterestCode;
import com.firstory.domain.child.entity.Interests;
import com.firstory.domain.child.entity.Trait;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

/**
 * 동화를 만들 때 AI 서비스로 보내는 아이 정보. story_inputs.child_snapshot(jsonb)에 저장한다.
 * 아이 호칭(displayName)은 절대 넣지 않는다.
 * TASK-04에서 필드가 바뀌었다(interests → interestCodes + customInterests, customTrait 추가).
 * 이전 형식의 jsonb는 로컬 개발 데이터뿐이라 호환 처리하지 않는다.
 *
 * @param customTrait traits에 OTHER가 없으면 null
 */
public record ChildSnapshot(int ageMonths, Gender gender, List<Trait> traits, String customTrait,
                            List<InterestCode> interestCodes, List<String> customInterests) {

    public ChildSnapshot {
        traits = traits == null ? List.of() : List.copyOf(traits);
        interestCodes = interestCodes == null ? List.of() : List.copyOf(interestCodes);
        customInterests = customInterests == null ? List.of() : List.copyOf(customInterests);
    }

    public static ChildSnapshot of(Child child, LocalDate today) {
        return new ChildSnapshot(child.ageMonthsAt(today), child.getGender(), sorted(child.getTraits()),
                child.getCustomTrait(), child.getInterestCodes(), child.getCustomInterests());
    }

    public static ChildSnapshot of(YearMonth birthYm, Gender gender, Collection<Trait> traits, String customTrait,
                                   Interests interests, LocalDate today) {
        int ageMonths = (int) ChronoUnit.MONTHS.between(birthYm, YearMonth.from(today));
        Interests normalized = Interests.normalize(interests.codes(), interests.customs());
        return new ChildSnapshot(ageMonths, gender, sorted(traits), Child.customTraitOf(traits, customTrait),
                normalized.codes(), normalized.customs());
    }

    private static List<Trait> sorted(Collection<Trait> traits) {
        return traits == null ? List.of() : traits.stream().distinct().sorted().toList();
    }
}
