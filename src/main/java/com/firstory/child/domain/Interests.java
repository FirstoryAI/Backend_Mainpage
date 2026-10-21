package com.firstory.child.domain;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 아이가 좋아하는 것 목록 정규화.
 */
public final class Interests {

    public static final int MAX_COUNT = 5;
    public static final int MAX_LENGTH = 20;

    private Interests() {
    }

    public static List<String> normalize(List<String> raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null) {
                continue;
            }
            String stripped = value.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            if (stripped.codePointCount(0, stripped.length()) > MAX_LENGTH) {
                throw new BusinessException(ErrorCode.INTEREST_TOO_LONG);
            }
            unique.add(stripped);
        }
        if (unique.size() > MAX_COUNT) {
            throw new BusinessException(ErrorCode.INTERESTS_LIMIT_EXCEEDED);
        }
        return new ArrayList<>(unique);
    }
}
