package com.firstory.domain.child.service;

import com.firstory.domain.child.dto.ChildProfile;
import com.firstory.domain.child.dto.ChildResponse;
import com.firstory.domain.child.entity.BirthYmPolicy;
import com.firstory.domain.child.entity.Child;
import com.firstory.domain.child.entity.ChildLabelPolicy;
import com.firstory.domain.child.repository.ChildRepository;
import com.firstory.domain.story.repository.StoryRepository;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.config.ClockConfig;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * 저장된 아이 프로필. 다른 회원의 아이는 존재 여부를 드러내지 않도록 항상 CHILD_NOT_FOUND(404)로 응답한다.
 */
@Service
@RequiredArgsConstructor
public class ChildService {

    private final ChildRepository childRepository;
    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ChildResponse> list(Long userId) {
        return childRepository.findAllByUserIdOrderByIdAsc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ChildResponse get(Long userId, Long childId) {
        return toResponse(findOwned(userId, childId));
    }

    @Transactional
    public ChildResponse create(Long userId, ChildProfile profile) {
        return toResponse(save(userId, profile));
    }

    @Transactional
    public ChildResponse update(Long userId, Long childId, ChildProfile profile) {
        Child child = findOwned(userId, childId);
        applyIfChanged(child, profile);
        return toResponse(child);
    }

    @Transactional
    public ChildResponse rename(Long userId, Long childId, String displayName) {
        Child child = findOwned(userId, childId);
        child.rename(displayName);
        return toResponse(child);
    }

    /**
     * 동화와의 연결을 먼저 끊고 소프트 삭제한다. 동화 내용은 child_snapshot이 있어서 영향이 없다.
     */
    @Transactional
    public void delete(Long userId, Long childId) {
        findOwned(userId, childId);
        // detachChild는 영속성 컨텍스트를 비운다. 그 전에 읽은 Child는 준영속이 되므로 다시 읽어서 지운다
        storyRepository.detachChild(childId);
        findOwned(userId, childId).delete(clock.instant());
    }

    /**
     * 새 프로필을 저장한다. 호칭은 비어 있는 가장 앞 글자("아이 A", "아이 B"…).
     */
    Child save(Long userId, ChildProfile profile) {
        BirthYmPolicy.validate(profile.birthYm(), clock);
        String label = ChildLabelPolicy.nextLabel(childRepository.findDisplayNamesByUserId(userId));
        return childRepository.save(Child.save(userRepository.getReferenceById(userId), label,
                profile.birthYm(), profile.gender(), profile.traits(), profile.interests()));
    }

    void applyIfChanged(Child child, ChildProfile profile) {
        BirthYmPolicy.validate(profile.birthYm(), clock);
        if (!child.hasProfile(profile.birthYm(), profile.gender(), profile.traits(), profile.interests())) {
            child.updateProfile(profile.birthYm(), profile.gender(), profile.traits(), profile.interests());
        }
    }

    Child findOwned(Long userId, Long childId) {
        return childRepository.findByIdAndUserId(childId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHILD_NOT_FOUND));
    }

    private ChildResponse toResponse(Child child) {
        return ChildResponse.of(child, ClockConfig.today(clock));
    }
}
