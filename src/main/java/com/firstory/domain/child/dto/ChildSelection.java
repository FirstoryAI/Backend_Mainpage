package com.firstory.domain.child.dto;

import java.util.Objects;

/**
 * 동화 만들기 CRT-03 화면에서 고른 아이 정보.
 *
 * @param childId         저장된 프로필을 불러왔으면 id, 새로 입력했으면 null
 * @param profile         화면에 최종으로 입력된 값
 * @param saveForNextTime "다음에도 이 아이 정보로 만들기" 체크 여부
 */
public record ChildSelection(Long childId, ChildProfile profile, boolean saveForNextTime) {

    public ChildSelection {
        Objects.requireNonNull(profile);
    }
}
