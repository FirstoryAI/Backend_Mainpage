package com.firstory.domain.child.dto;

import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.Trait;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Clock;
import java.util.List;

/**
 * traits, interests는 생략하면 빈 목록. 없는 enum 값은 VALIDATION_FAILED.
 */
public record ChildProfileRequest(
        @NotBlank(message = "생년월을 입력해 주세요.") String birthYm,
        @NotNull(message = "성별을 선택해 주세요.") Gender gender,
        List<Trait> traits,
        List<String> interests) {

    public ChildProfile toProfile(Clock clock) {
        return ChildProfile.of(birthYm, gender, traits, interests, clock);
    }

    // 아이 정보가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "ChildProfileRequest[***]";
    }
}
