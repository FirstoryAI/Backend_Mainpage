package com.firstory.child.domain;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;

import java.util.Collection;

/**
 * 아이 기본 호칭("아이 A" ~ "아이 Z") 부여.
 */
public final class ChildLabelPolicy {

    private static final String PREFIX = "아이 ";

    private ChildLabelPolicy() {
    }

    /**
     * 사용 중이 아닌 가장 앞 글자의 호칭을 돌려준다.
     */
    public static String nextLabel(Collection<String> labelsInUse) {
        for (char c = 'A'; c <= 'Z'; c++) {
            String label = PREFIX + c;
            if (labelsInUse == null || !labelsInUse.contains(label)) {
                return label;
            }
        }
        throw new BusinessException(ErrorCode.CHILD_LABEL_EXHAUSTED);
    }
}
