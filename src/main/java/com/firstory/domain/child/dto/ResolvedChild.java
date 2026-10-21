package com.firstory.domain.child.dto;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.story.entity.ChildSnapshot;

import java.util.Objects;

/**
 * @param child    동화에 연결할 프로필. 저장하지 않았으면 null
 * @param snapshot 동화에 저장하고 AI로 보낼 정보. 호칭은 없다
 */
public record ResolvedChild(Child child, ChildSnapshot snapshot) {

    public ResolvedChild {
        Objects.requireNonNull(snapshot);
    }

    @Override
    public String toString() {
        return "ResolvedChild[childId=" + (child == null ? null : child.getId()) + "]";
    }
}
