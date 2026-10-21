package com.firstory.domain.user.entity;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;

import java.util.Map;

public final class ConsentPolicy {

    private ConsentPolicy() {
    }

    /**
     * 필수 동의 항목이 모두 true인지 확인한다. 선택 항목은 검사하지 않는다.
     */
    public static void validateRequired(Map<ConsentType, Boolean> agreements) {
        for (ConsentType type : ConsentType.values()) {
            if (type.isRequired() && !isAgreed(agreements, type)) {
                throw new BusinessException(ErrorCode.REQUIRED_CONSENT_MISSING);
            }
        }
    }

    private static boolean isAgreed(Map<ConsentType, Boolean> agreements, ConsentType type) {
        return agreements != null && Boolean.TRUE.equals(agreements.get(type));
    }
}
