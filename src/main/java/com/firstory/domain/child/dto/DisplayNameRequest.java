package com.firstory.domain.child.dto;

/**
 * 길이(1~30자) 검사는 Child.rename이 INVALID_CHILD_NAME으로 한다.
 */
public record DisplayNameRequest(String displayName) {

    // 호칭이 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "DisplayNameRequest[***]";
    }
}
