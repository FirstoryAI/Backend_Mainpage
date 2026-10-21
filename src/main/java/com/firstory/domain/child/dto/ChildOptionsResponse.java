package com.firstory.domain.child.dto;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.InterestCode;
import com.firstory.domain.child.entity.Interests;
import com.firstory.domain.child.entity.Trait;

import java.util.Arrays;
import java.util.List;

/**
 * 프론트가 성향, 성별, 좋아하는 것 버튼 문구와 제한을 하드코딩하지 않도록 내려주는 선택지.
 * 문구(label)는 enum에만 둔다.
 *
 * @param interestsMax 고른 것과 직접 입력을 합친 최대 개수
 */
public record ChildOptionsResponse(List<Option> traits, List<Option> genders, List<Option> interests,
                                   int interestsMax, int customTraitMaxLength, int customInterestMaxLength) {

    public record Option(String code, String label) {
    }

    public static ChildOptionsResponse create() {
        return new ChildOptionsResponse(
                Arrays.stream(Trait.values()).map(t -> new Option(t.name(), t.getLabel())).toList(),
                Arrays.stream(Gender.values()).map(g -> new Option(g.name(), g.getLabel())).toList(),
                Arrays.stream(InterestCode.values()).map(i -> new Option(i.name(), i.getLabel())).toList(),
                Interests.MAX_COUNT,
                Child.CUSTOM_TRAIT_MAX_LENGTH,
                Interests.CUSTOM_MAX_LENGTH);
    }
}
