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
import com.firstory.domain.story.service.BlockedTopics;
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
 * REVISE     SafetyFlag                                 SafetyPolicy.decide → ASK_REVISE 또는 BLOCK
 * CRISIS     SafetyFlag                                 BLOCK
 * </pre>
 * topic이 막은 주제(BlockedTopics, 이별·죽음)면 CRISIS가 아닌 한 level과 상관없이 ASK_REVISE(고정 문구)다.
 * 부모 잘못이 아니라서 SafetyFlag를 남기지 않고 REVISE 횟수에도 넣지 않는다(몇 번이어도 차단하지 않는다).
 * BLOCK이면 story.block(). helpKind는 CRISIS면 CHILD_SAFETY, REVISE로 차단이면 PARENTING.
 * 차단 판단의 REVISE 횟수에는 AI가 만든 결과(OUTPUT)의 위기 신호를 넣지 않는다 (부모 책임이 아니다).
 * <p>
 * 인터뷰 답변은 AI가 다음 질문 만들기와 동시에 판단해 응답에 담아 주므로 {@link #apply}로 그 판단만 적용한다.
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

    // TODO(PM): 문구 확정 (#79)
    public static final String BLOCKED_TOPIC_REASON =
            "지금은 이별이나 죽음을 다루는 이야기는 만들 수 없어요. 다른 이야기로 바꿔 써 주세요.";

    private final AiClient aiClient;
    private final SafetyPolicy safetyPolicy;
    private final BlockedTopics blockedTopics;
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
        return apply(story, source, aiClient.checkSafety(source, written, questionContext), turnOrNull);
    }

    /**
     * AI가 이미 판단한 결과를 기록하고 대응을 정한다 (인터뷰 답변: AI가 interview/answer 응답에 담아 준다).
     *
     * @param story      ①에서 조회한 동화. 이 상태 그대로여야 기록한다
     * @param turnOrNull 인터뷰 답변이면 그 차례
     */
    public SafetyOutcome apply(Story story, SafetySource source, SafetyResult result, InterviewTurn turnOrNull) {
        if (result.level() != SafetyLevel.CRISIS && blockedTopics.blocksAi(result.topic())) {
            return new SafetyOutcome(SafetyAction.ASK_REVISE, result.level(), result.topic(), BLOCKED_TOPIC_REASON, null);
        }
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
     * @return 이번 것을 포함한 이 동화의 같은 level 횟수 (결과 위기 신호 OUTPUT은 빼고 센다)
     */
    private int saveFlag(Story story, SafetySource source, SafetyResult result, InterviewTurn turn) {
        int parentCount = (int) safetyFlagRepository.countByStoryIdAndLevelAndSourceNot(story.getId(), result.level(),
                SafetySource.OUTPUT) + 1;
        // 순번은 OUTPUT까지 포함해 센다 (UNIQUE story, level, occurrence_no)
        int occurrenceNo = (int) safetyFlagRepository.countByStoryIdAndLevel(story.getId(), result.level()) + 1;
        String category = result.category() == null || result.category().isBlank()
                ? UNKNOWN_CATEGORY : result.category();
        safetyFlagRepository.save(SafetyFlag.record(story, source, result.level(), turn, category, result.reason(),
                occurrenceNo, clock.instant()));
        return parentCount;
    }
}
