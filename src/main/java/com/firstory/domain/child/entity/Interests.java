package com.firstory.domain.child.entity;

import com.firstory.global.entity.CustomText;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 아이가 좋아하는 것. 목록에서 고른 값(codes)과 직접 입력(customs)을 합쳐 최대 5개.
 *
 * @param codes   중복 없이 InterestCode 선언 순서
 * @param customs 앞뒤 공백을 지우고 빈 값과 중복을 뺀 입력 순서
 */
public record Interests(List<InterestCode> codes, List<String> customs) {

    public static final int MAX_COUNT = 5;
    public static final int CUSTOM_MAX_LENGTH = 20;

    public Interests {
        codes = codes == null ? List.of() : List.copyOf(codes);
        customs = customs == null ? List.of() : List.copyOf(customs);
    }

    public static Interests normalize(Collection<InterestCode> codes, Collection<String> customs) {
        Set<InterestCode> codeSet = EnumSet.noneOf(InterestCode.class);
        if (codes != null) {
            codes.stream().filter(Objects::nonNull).forEach(codeSet::add);
        }
        Set<String> customSet = new LinkedHashSet<>();
        if (customs != null) {
            for (String raw : customs) {
                String stripped = CustomText.strip(raw);
                if (stripped != null) {
                    customSet.add(CustomText.checkLength(stripped, CUSTOM_MAX_LENGTH));
                }
            }
        }
        if (codeSet.size() + customSet.size() > MAX_COUNT) {
            throw new BusinessException(ErrorCode.INTERESTS_LIMIT_EXCEEDED);
        }
        return new Interests(List.copyOf(codeSet), List.copyOf(customSet));
    }

    public static Interests empty() {
        return new Interests(List.of(), List.of());
    }
}
