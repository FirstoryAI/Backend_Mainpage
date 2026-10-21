package com.firstory.domain.child.service;

import com.firstory.domain.child.dto.ChildProfile;
import com.firstory.domain.child.dto.ChildSelection;
import com.firstory.domain.child.dto.ResolvedChild;
import com.firstory.domain.child.entity.BirthYmPolicy;
import com.firstory.domain.child.entity.Child;
import com.firstory.domain.story.entity.ChildSnapshot;
import com.firstory.global.config.ClockConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 동화 만들기(CRT-03)에서 고른 아이 정보를 프로필 저장 여부에 맞게 처리한다. 동화 만들기(TASK-05)가 이것 하나만 부른다.
 *
 * <pre>
 * childId  체크  처리                              동화의 child
 * 없음     O     새 프로필 저장 (호칭 자동 부여)    새 프로필
 * 없음     X     저장하지 않음                      null
 * 있음     O     입력값으로 갱신 (같으면 그대로)    그 프로필
 * 있음     X     프로필은 그대로                    그 프로필
 * 남의 것  -     CHILD_NOT_FOUND
 * </pre>
 * snapshot은 항상 화면의 최종 입력값으로 만든다. 프로필에서 읽지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChildProfileResolver {

    private final ChildService childService;
    private final Clock clock;

    @Transactional
    public ResolvedChild resolve(Long userId, ChildSelection selection) {
        ChildProfile profile = selection.profile();
        // 저장 여부와 상관없이 항상 검증한다
        BirthYmPolicy.validate(profile.birthYm(), clock);
        ChildSnapshot snapshot = ChildSnapshot.of(profile.birthYm(), profile.gender(), profile.traits(),
                profile.customTrait(), profile.interests(), ClockConfig.today(clock));

        if (selection.childId() == null) {
            Child saved = selection.saveForNextTime() ? childService.save(userId, profile) : null;
            return new ResolvedChild(saved, snapshot);
        }

        Child child = childService.findOwned(userId, selection.childId());
        if (selection.saveForNextTime()) {
            childService.applyIfChanged(child, profile);
        }
        return new ResolvedChild(child, snapshot);
    }
}
