package com.firstory.domain.story.dto;

import com.firstory.domain.child.dto.ChildProfileRequest;
import com.firstory.domain.child.dto.ChildSelection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Clock;

/**
 * 동화 만들기 CRT-03에서 고른 아이 정보 (ChildSelection 형식).
 *
 * @param childId         저장된 프로필을 불러왔으면 id, 새로 입력했으면 null
 * @param profile         화면에 최종으로 입력된 값
 * @param saveForNextTime "다음에도 이 아이 정보로 만들기" 체크 여부
 */
public record StoryChildRequest(
        Long childId,
        @Valid @NotNull(message = "아이 정보를 입력해 주세요.") ChildProfileRequest profile,
        boolean saveForNextTime) {

    public ChildSelection toSelection(Clock clock) {
        return new ChildSelection(childId, profile.toProfile(clock), saveForNextTime);
    }

    // 아이 정보가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "StoryChildRequest[childId=" + childId + "]";
    }
}
