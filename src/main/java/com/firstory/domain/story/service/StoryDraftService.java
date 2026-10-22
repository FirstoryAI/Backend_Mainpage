package com.firstory.domain.story.service;

import com.firstory.domain.child.dto.ChildProfile;
import com.firstory.domain.child.dto.ChildSelection;
import com.firstory.domain.child.dto.ResolvedChild;
import com.firstory.domain.child.entity.BirthYmPolicy;
import com.firstory.domain.child.repository.ChildRepository;
import com.firstory.domain.child.service.ChildProfileResolver;
import com.firstory.domain.safety.entity.SafetyOutcome;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.safety.service.SafetyGuard;
import com.firstory.domain.story.dto.MessageRequest;
import com.firstory.domain.story.dto.SituationRequest;
import com.firstory.domain.story.dto.StoryChildRequest;
import com.firstory.domain.story.dto.StoryCreatedResponse;
import com.firstory.domain.story.dto.StoryDraftResponse;
import com.firstory.domain.story.dto.StoryStepResponse;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryInput;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.entity.Tone;
import com.firstory.domain.story.repository.InterviewTurnRepository;
import com.firstory.domain.story.repository.StoryInputRepository;
import com.firstory.domain.story.repository.StoryRepository;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.entity.CustomText;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 동화 만들기 입력 (CRT-01~04, ST-04), 이어서 만들기 조회, 나가기.
 * <p>
 * 위기 신호를 검사하는 단계는 AI 응답이 30초 넘게 걸릴 수 있어 메서드에 @Transactional을 달지 않는다.
 * ① 트랜잭션으로 조회·검증 → ② 트랜잭션 밖에서 위기 신호 검사(SafetyGuard) → ③ 새 트랜잭션으로 저장.
 * ③에서 동화를 다시 읽어 상태가 바뀌었으면 INVALID_STATUS_TRANSITION.
 * 다시 쓰기 안내(ASK_REVISE)와 차단(BLOCK)이면 입력을 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class StoryDraftService {

    private static final Set<StoryStatus> DRAFT = EnumSet.of(StoryStatus.DRAFT);
    private static final Set<StoryStatus> TONE_EDITABLE =
            EnumSet.of(StoryStatus.DRAFT, StoryStatus.INTERVIEWING, StoryStatus.CONFIRMING);
    private static final Set<StoryStatus> DELETABLE =
            EnumSet.of(StoryStatus.DRAFT, StoryStatus.INTERVIEWING, StoryStatus.CONFIRMING, StoryStatus.BLOCKED);

    private final StoryLoader loader;
    private final StoryRepository storyRepository;
    private final StoryInputRepository storyInputRepository;
    private final InterviewTurnRepository interviewTurnRepository;
    private final UserRepository userRepository;
    private final ChildRepository childRepository;
    private final ChildProfileResolver childProfileResolver;
    private final SafetyGuard safetyGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Transactional
    public StoryCreatedResponse create(Long userId) {
        Story story = storyRepository.save(Story.start(userRepository.getReferenceById(userId), null,
                UUID.randomUUID().toString()));
        storyInputRepository.save(StoryInput.begin(story));
        return new StoryCreatedResponse(story.getId(), story.getStatus());
    }

    /**
     * CRT-01, 02. 형식 검증을 먼저 해서 형식 오류는 AI를 부르기 전에 400으로 끝낸다.
     */
    public StoryStepResponse writeSituation(Long userId, Long storyId, SituationRequest request) {
        StoryInput.validateSituation(request.timing(), request.category(), request.customCategory(),
                request.description(), request.reactions(), request.customReaction(), request.emotions());
        return checkThenSave(userId, storyId, request.textsToCheck(), input -> input.writeSituation(
                request.timing(), request.category(), request.customCategory(), request.description(),
                request.reactions(), request.customReaction(), request.emotions()));
    }

    /**
     * CRT-03. 직접 입력(성향, 좋아하는 것)을 검사하고, 통과하면 ChildProfileResolver로 프로필 저장 여부를 처리한다.
     */
    public StoryStepResponse writeChild(Long userId, Long storyId, StoryChildRequest request) {
        ChildSelection selection = request.toSelection(clock);
        ChildProfile profile = selection.profile();
        BirthYmPolicy.validate(profile.birthYm(), clock);
        List<String> texts = new ArrayList<>(profile.customInterests());
        texts.add(profile.customTrait());
        return checkThenSave(userId, storyId, texts, () -> {
            // 남의 아이는 AI를 부르기 전에 404로 끝낸다
            if (selection.childId() != null && childRepository.findByIdAndUserId(selection.childId(), userId).isEmpty()) {
                throw new BusinessException(ErrorCode.CHILD_NOT_FOUND);
            }
        }, (story, input) -> {
            ResolvedChild resolved = childProfileResolver.resolve(userId, selection);
            story.assignChild(resolved.child());
            input.writeChild(resolved.snapshot());
        });
    }

    /**
     * CRT-04. 비어 있으면 "아직 잘 모르겠어요"로 보고 null로 저장한다.
     */
    public StoryStepResponse writeMessage(Long userId, Long storyId, MessageRequest request) {
        String message = CustomText.strip(request.message());
        return checkThenSave(userId, storyId, Collections.singletonList(message),
                input -> input.writeParent(null, null, message, null));
    }

    /**
     * ST-04. 위기 신호 검사 없이 바로 저장한다.
     */
    @Transactional
    public StoryStepResponse chooseTone(Long userId, Long storyId, Tone tone) {
        Story story = loader.findEditable(userId, storyId, TONE_EDITABLE);
        StoryInput input = loader.input(story);
        input.chooseTone(tone);
        return StoryStepResponse.of(story.getId(), story.getStatus(), true, null, input.isToneRequired());
    }

    @Transactional(readOnly = true)
    public StoryDraftResponse getDraft(Long userId, Long storyId) {
        Story story = loader.findOwned(userId, storyId);
        return StoryDraftResponse.of(story, loader.input(story),
                interviewTurnRepository.findAllByStoryIdOrderByTurnNoAsc(story.getId()));
    }

    /**
     * 만드는 중에 나가기. 생성 요청 이후의 동화는 서재에서 지운다 (TASK-07).
     */
    @Transactional
    public void delete(Long userId, Long storyId) {
        Story story = loader.findOwned(userId, storyId);
        if (!DELETABLE.contains(story.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        story.delete(clock.instant());
    }

    private StoryStepResponse checkThenSave(Long userId, Long storyId, List<String> texts, Consumer<StoryInput> write) {
        return checkThenSave(userId, storyId, texts, () -> {
        }, (story, input) -> write.accept(input));
    }

    private StoryStepResponse checkThenSave(Long userId, Long storyId, List<String> texts, Runnable verify,
                                            Writer write) {
        // ① 조회·검증
        Loaded loaded = transactionTemplate.execute(status -> {
            Story story = loader.findEditable(userId, storyId, DRAFT);
            verify.run();
            return new Loaded(story, loader.input(story).isToneRequired());
        });
        // ② 트랜잭션 밖에서 AI 호출. 기록과 차단은 SafetyGuard가 자기 트랜잭션에서 한다
        SafetyOutcome outcome = safetyGuard.check(loaded.story(), SafetySource.FORM, texts, null, null);
        if (!outcome.canContinue()) {
            StoryStatus status = outcome.isBlocked() ? StoryStatus.BLOCKED : loaded.story().getStatus();
            return StoryStepResponse.of(storyId, status, false, outcome, loaded.toneRequired());
        }
        // ③ 저장
        return transactionTemplate.execute(status -> {
            Story story = loader.lockUnchanged(loaded.story());
            StoryInput input = loader.input(story);
            write.write(story, input);
            return StoryStepResponse.of(story.getId(), story.getStatus(), true, outcome, input.isToneRequired());
        });
    }

    private record Loaded(Story story, boolean toneRequired) {
    }

    @FunctionalInterface
    private interface Writer {
        void write(Story story, StoryInput input);
    }
}
