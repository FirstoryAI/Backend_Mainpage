package com.firstory.domain.child.dto;

import com.firstory.domain.child.entity.BirthYmPolicy;
import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.Interests;
import com.firstory.domain.child.entity.Trait;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 화면에 입력된 아이 정보 (생년월, 성별, 성향, 좋아하는 것). 호칭은 없다.
 * 성향은 중복과 null을 지우고, 좋아하는 것은 Interests.normalize로 정리한다.
 * 생년월 범위는 기준 시각이 필요해서 of()와 사용하는 쪽(BirthYmPolicy.validate)에서 검사한다.
 */
public record ChildProfile(YearMonth birthYm, Gender gender, Set<Trait> traits, List<String> interests) {

    public ChildProfile {
        if (birthYm == null) {
            throw new BusinessException(ErrorCode.INVALID_BIRTH_YM);
        }
        if (gender == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        traits = Collections.unmodifiableSet(toTraitSet(traits));
        interests = List.copyOf(Interests.normalize(interests));
    }

    public static ChildProfile of(String birthYm, Gender gender, Collection<Trait> traits, List<String> interests,
                                  Clock clock) {
        return new ChildProfile(BirthYmPolicy.parse(birthYm, clock), gender, toTraitSet(traits), interests);
    }

    private static Set<Trait> toTraitSet(Collection<Trait> traits) {
        Set<Trait> set = EnumSet.noneOf(Trait.class);
        if (traits != null) {
            traits.stream().filter(Objects::nonNull).forEach(set::add);
        }
        return set;
    }
}
