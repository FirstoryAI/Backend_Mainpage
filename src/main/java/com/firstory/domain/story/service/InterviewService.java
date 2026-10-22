package com.firstory.domain.story.service;

import com.firstory.domain.safety.entity.SafetyAction;
import com.firstory.domain.safety.entity.SafetyOutcome;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.safety.service.SafetyGuard;
import com.firstory.domain.story.dto.InterviewResponse;
import com.firstory.domain.story.entity.InterviewTurn;
import com.firstory.domain.story.entity.MessageDirection;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryInput;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.entity.Tone;
import com.firstory.domain.story.repository.InterviewTurnRepository;
import com.firstory.global.ai.AiClient;
import com.firstory.global.ai.InterviewReply;
import com.firstory.global.entity.CustomText;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AI 인터뷰 (CRT-05). AI 응답이 30초 넘게 걸릴 수 있어 메서드에 @Transactional을 달지 않는다.
 * ① 트랜잭션으로 조회·검증 → ② 트랜잭션 밖에서 AI 호출 → ③ 새 트랜잭션으로 저장 (상태가 바뀌었으면 INVALID_STATUS_TRANSITION).
 * <p>
 * 답변은 AI 그래프에 보내기 전에 위기 신호를 검사한다. 다시 쓰기 안내나 차단이면 답변을 저장하지도, 그래프로 보내지도 않는다.
 * AI 호출이 실패하면 아무것도 저장하지 않아서 같은 답변으로 다시 보낼 수 있다.
 */
// TODO(AI 연동): AI가 답변을 처리한 뒤 응답만 잃어버린 경우(타임아웃) 다시 보내면 AI_STATE_MISMATCH가 날 수 있다.
//  진짜 AI 서버가 생기면 status API로 맞추는 처리를 TASK-06에서 추가한다
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final Set<StoryStatus> DRAFT = EnumSet.of(StoryStatus.DRAFT);
    private static final Set<StoryStatus> INTERVIEWING = EnumSet.of(StoryStatus.INTERVIEWING);

    private final StoryLoader loader;
    private final InterviewTurnRepository interviewTurnRepository;
    private final AiClient aiClient;
    private final SafetyGuard safetyGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 상황과 아이 정보가 있어야 시작한다. context에 아이 호칭은 없다 (AiContextMapper).
     */
    public InterviewResponse start(Long userId, Long storyId) {
        // ①
        StartContext start = transactionTemplate.execute(status -> {
            Story story = loader.findEditable(userId, storyId, DRAFT);
            StoryInput input = loader.input(story);
            if (!input.hasSituation() || input.getChildSnapshot() == null) {
                throw new BusinessException(ErrorCode.STORY_INPUT_INCOMPLETE);
            }
            return new StartContext(story, AiContextMapper.toContext(input), input.getTone());
        });
        // ②
        InterviewReply reply = aiClient.startInterview(start.story().getThreadId(), UUID.randomUUID(),
                start.context(), start.tone());
        // ③
        return transactionTemplate.execute(status -> {
            Story story = loader.lockUnchanged(start.story());
            StoryInput input = loader.input(story);
            story.changeStatus(StoryStatus.INTERVIEWING);
            return apply(story, input, 0, reply, null);
        });
    }

    /**
     * @param answer 앞뒤 공백을 지운 답변. null이면 건너뛰기 (글이 없어 위기 신호를 검사하지 않는다)
     */
    public InterviewResponse answer(Long userId, Long storyId, String answer) {
        // ①
        Current current = transactionTemplate.execute(status -> {
            Story story = loader.findEditable(userId, storyId, INTERVIEWING);
            InterviewTurn turn = currentTurn(story);
            return new Current(story, turn, loader.input(story).isToneRequired());
        });
        Story story = current.story();
        InterviewTurn turn = current.turn();

        // ② 그래프에 보내기 전에 위기 신호 검사
        SafetyOutcome outcome = null;
        if (answer != null) {
            outcome = safetyGuard.check(story, SafetySource.INTERVIEW, List.of(answer), turn, turn.getQuestion());
            if (outcome.isBlocked()) {
                return InterviewResponse.blocked(story.getId(), outcome, current.toneRequired());
            }
            if (outcome.action() == SafetyAction.ASK_REVISE) {
                return InterviewResponse.question(story.getId(), story.getStatus(), turn.getTurnNo(),
                        turn.getQuestion(), null, outcome, current.toneRequired());
            }
        }
        InterviewReply reply = aiClient.answerInterview(story.getThreadId(), answer);

        // ③
        SafetyOutcome checked = outcome;
        return transactionTemplate.execute(status -> {
            Story locked = loader.lockUnchanged(story);
            InterviewTurn answered = interviewTurnRepository.findById(turn.getId())
                    .filter(saved -> !saved.isAnswered())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION));
            if (answer != null) {
                answered.answer(answer, clock.instant());
            }
            if (reply.contextUpdate() != null) {
                answered.updateContext(reply.contextUpdate());
            }
            return apply(locked, loader.input(locked), answered.getTurnNo(), reply, checked);
        });
    }

    private InterviewTurn currentTurn(Story story) {
        return interviewTurnRepository.findTopByStoryIdOrderByTurnNoDesc(story.getId())
                .filter(turn -> !turn.isAnswered())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    /**
     * AI 응답을 저장한다. QUESTION이면 다음 차례를 만들고, CONFIRM이면 인터뷰를 끝내고 CONFIRMING으로 간다.
     *
     * @param lastTurnNo 방금 답한 차례. 시작이면 0
     */
    private InterviewResponse apply(Story story, StoryInput input, int lastTurnNo, InterviewReply reply,
                                    SafetyOutcome outcome) {
        if (reply.isConfirm()) {
            input.finishInterview(reply.summary(), reply.endReason(), reply.finalContext());
            writeParentFrom(story, input, reply.finalContext());
            story.changeStatus(StoryStatus.CONFIRMING);
            return InterviewResponse.confirm(story.getId(), story.getStatus(), reply.summary(), outcome,
                    input.isToneRequired());
        }
        int nextTurnNo = lastTurnNo + 1;
        if (reply.turn() != null && reply.turn() != nextTurnNo) {
            log.warn("AI 인터뷰 차례가 맞지 않아요: thread_id={}, expected={}, actual={}",
                    story.getThreadId(), nextTurnNo, reply.turn());
        }
        if (nextTurnNo > InterviewTurn.MAX_TURNS) {
            log.warn("AI가 최대 차례({})를 넘겨 질문했어요: thread_id={}", InterviewTurn.MAX_TURNS, story.getThreadId());
            throw new BusinessException(ErrorCode.AI_STATE_MISMATCH);
        }
        interviewTurnRepository.save(InterviewTurn.ask(story, nextTurnNo, reply.question()));
        return InterviewResponse.question(story.getId(), story.getStatus(), nextTurnNo, reply.question(),
                reply.contextUpdate(), outcome, input.isToneRequired());
    }

    /**
     * 인터뷰가 채운 parent 값으로 바꾼다. 값을 읽을 수 없으면 null로 두고 경고를 남긴다 (값 자체는 남기지 않는다).
     */
    private static void writeParentFrom(Story story, StoryInput input, Map<String, Object> finalContext) {
        if (finalContext == null || !(finalContext.get("parent") instanceof Map<?, ?> parent)) {
            log.warn("AI 최종 context에 parent가 없어요: thread_id={}", story.getThreadId());
            return;
        }
        input.writeParent(text(story, parent, "response"), text(story, parent, "concern"),
                text(story, parent, "message"), direction(story, parent.get("message_direction")));
    }

    private static String text(Story story, Map<?, ?> parent, String key) {
        Object value = parent.get(key);
        if (value == null || value instanceof String) {
            return CustomText.strip((String) value);
        }
        log.warn("AI 최종 context의 parent.{}가 글이 아니에요: thread_id={}", key, story.getThreadId());
        return null;
    }

    private static MessageDirection direction(Story story, Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MessageDirection.valueOf(((String) value).toUpperCase(Locale.ROOT));
        } catch (ClassCastException | IllegalArgumentException e) {
            log.warn("AI 최종 context의 parent.message_direction을 알 수 없어요: thread_id={}", story.getThreadId());
            return null;
        }
    }

    private record StartContext(Story story, Map<String, Object> context, Tone tone) {
    }

    private record Current(Story story, InterviewTurn turn, boolean toneRequired) {
    }
}
