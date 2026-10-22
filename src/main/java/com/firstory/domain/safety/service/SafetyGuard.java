package com.firstory.domain.safety.service;

import com.firstory.domain.safety.entity.HelpKind;
import com.firstory.domain.safety.entity.SafetyAction;
import com.firstory.domain.safety.entity.SafetyFlag;
import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetyOutcome;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.safety.repository.SafetyFlagRepository;
import com.firstory.domain.story.entity.InterviewTurn;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryInput;
import com.firstory.domain.story.repository.StoryInputRepository;
import com.firstory.domain.story.repository.StoryRepository;
import com.firstory.global.ai.AiClient;
import com.firstory.global.ai.SafetyResult;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.List;

/**
 * 부모가 쓴 글을 AI에 보내 위기 신호를 판단하고, 결과를 기록하고 대응을 정한다.
 *
 * <pre>
 * level      기록                                       action
 * OK         없음                                       PROCEED
 * SENSITIVE  storyInput.markToneRequired()               tone이 있으면 PROCEED, 없으면 CHOOSE_TONE
 * REVISE     SafetyFlag (occurrenceNo = REVISE 수 + 1)   SafetyPolicy.decide → ASK_REVISE 또는 BLOCK
 * CRISIS     SafetyFlag                                 BLOCK
 * </pre>
 * BLOCK이면 story.block(). helpKind는 CRISIS면 CHILD_SAFETY, REVISE로 차단이면 PARENTING.
 * <p>
 * 트랜잭션 밖에서 불러야 한다. AI 호출은 트랜잭션 밖에서 하고, 기록은 새 트랜잭션에서 한다.
 * 그 사이에 동화 상태가 바뀌었으면 INVALID_STATUS_TRANSITION.
 */
// TODO(PM): 차단된 입력을 보관할지 바로 지울지 (ST-05 확인 필요). 지금은 그대로 둔다
@Service
@RequiredArgsConstructor
public class SafetyGuard {

    // AI가 category를 비워 보내도 기록할 수 있게 한다 (safety_flags.category NOT NULL)
    static final String UNKNOWN_CATEGORY = "other";

    private final AiClient aiClient;
    private final SafetyPolicy safetyPolicy;
    private final SafetyFlagRepository safetyFlagRepository;
    private final StoryRepository storyRepository;
    private final StoryInputRepository storyInputRepository;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    /**
     * @param story           ①에서 조회한 동화. 이 상태 그대로여야 기록한다
     * @param turnOrNull      인터뷰 답변이면 그 차례. SafetyFlag.interviewTurn에 남는다
     * @param questionContext 인터뷰 답변이면 직전 질문
     */
    public SafetyOutcome check(Story story, SafetySource source, List<String> texts, InterviewTurn turnOrNull,
                               String questionContext) {
        List<String> written = texts.stream().filter(text -> text != null && !text.isBlank()).toList();
        if (written.isEmpty()) {
            return SafetyOutcome.proceed();
        }
        SafetyResult result = aiClient.checkSafety(source, written, questionContext);
        if (result.level() == SafetyLevel.OK) {
            return SafetyOutcome.proceed();
        }
        return transactionOperations.execute(status -> record(story, source, result, turnOrNull));
    }

    private SafetyOutcome record(Story expected, SafetySource source, SafetyResult result, InterviewTurn turn) {
        Story story = storyRepository.findByIdForUpdate(expected.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
        if (story.getStatus() != expected.getStatus()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        SafetyLevel level = result.level();
        SafetyAction action = switch (level) {
            case OK -> SafetyAction.PROCEED;
            case SENSITIVE -> {
                StoryInput input = storyInputRepository.findById(story.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
                input.markToneRequired();
                yield input.getTone() != null ? SafetyAction.PROCEED : SafetyAction.CHOOSE_TONE;
            }
            case REVISE -> safetyPolicy.decide(level, saveFlag(story, source, result, turn));
            case CRISIS -> {
                saveFlag(story, source, result, turn);
                yield safetyPolicy.decide(level, 0);
            }
        };
        HelpKind helpKind = null;
        if (action == SafetyAction.BLOCK) {
            story.block();
            helpKind = level == SafetyLevel.CRISIS ? HelpKind.CHILD_SAFETY : HelpKind.PARENTING;
        }
        return new SafetyOutcome(action, level, result.category(), result.reason(), helpKind);
    }

    /**
     * @return 이번 것을 포함한 이 동화의 같은 level 횟수
     */
    private int saveFlag(Story story, SafetySource source, SafetyResult result, InterviewTurn turn) {
        int occurrenceNo = (int) safetyFlagRepository.countByStoryIdAndLevel(story.getId(), result.level()) + 1;
        String category = result.category() == null || result.category().isBlank()
                ? UNKNOWN_CATEGORY : result.category();
        safetyFlagRepository.save(SafetyFlag.record(story, source, result.level(), turn, category, result.reason(),
                occurrenceNo, clock.instant()));
        return occurrenceNo;
    }
}
