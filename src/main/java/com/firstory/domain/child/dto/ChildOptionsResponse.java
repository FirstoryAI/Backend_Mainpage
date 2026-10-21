package com.firstory.domain.child.dto;

import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.Interests;
import com.firstory.domain.child.entity.Trait;

import java.util.Arrays;
import java.util.List;

/**
 * 프론트가 성향, 성별 버튼 문구와 좋아하는 것 제한을 하드코딩하지 않도록 내려주는 선택지.
 */
public record ChildOptionsResponse(List<Option> traits, List<Option> genders, int interestsMax,
                                   int interestMaxLength) {

    public record Option(String code, String label) {
    }

    public static ChildOptionsResponse create() {
        return new ChildOptionsResponse(
                Arrays.stream(Trait.values()).map(t -> new Option(t.name(), t.getLabel())).toList(),
                Arrays.stream(Gender.values()).map(g -> new Option(g.name(), g.getLabel())).toList(),
                Interests.MAX_COUNT,
                Interests.MAX_LENGTH);
    }
}
