package com.firstory.domain.child.dto;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.Trait;

import java.time.LocalDate;
import java.util.List;

/**
 * 부모에게 보여주는 아이 프로필. 호칭은 부모에게만 보이고 AI로는 보내지 않는다.
 *
 * @param traits Trait 선언 순서로 정렬
 */
public record ChildResponse(Long id, String displayName, String birthYm, int ageMonths, Gender gender,
                            List<Trait> traits, List<String> interests) {

    public static ChildResponse of(Child child, LocalDate today) {
        return new ChildResponse(child.getId(), child.getDisplayName(), child.getBirthYm().toString(),
                child.ageMonthsAt(today), child.getGender(),
                child.getTraits().stream().sorted().toList(), List.copyOf(child.getInterests()));
    }

    @Override
    public String toString() {
        return "ChildResponse[id=" + id + "]";
    }
}
