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
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.entity.Tone;
import com.firstory.domain.story.repository.StoryInputRepository;
import com.firstory.domain.story.repository.StoryRepository;
import com.firstory.domain.generation.entity.RegenerateScope;
import com.firstory.global.ai.AiClient;
import com.firstory.global.ai.AiGenerationResult;
import com.firstory.global.ai.AiGenerationStatus;
import com.firstory.global.ai.InterviewReply;
import com.firstory.global.ai.SafetyResult;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.firstory.support.Fixtures.NOW;
import static com.firstory.support.Fixtures.story;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spring 없이 SafetyGuard의 대응 표를 확인한다. 저장소는 mock, 트랜잭션은 없이 돈다.
 */
class SafetyGuardTest {

    static final int REVISE_LIMIT = 2;

    StubAiClient ai = new StubAiClient();
    SafetyFlagRepository flags = mock(SafetyFlagRepository.class);
    StoryRepository stories = mock(StoryRepository.class);
    StoryInputRepository inputs = mock(StoryInputRepository.class);
    SafetyGuard guard;

    Story story;
    StoryInput input;

    @BeforeEach
    void setUp() {
        SafetyPolicy policy = new SafetyPolicy(new FirstoryProperties(null, null,
                new FirstoryProperties.Policy(3, REVISE_LIMIT), null, null, null, null, null));
        guard = new SafetyGuard(ai, policy, flags, stories, inputs,
                TransactionOperations.withoutTransaction(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        story = story(user());
        input = StoryInput.begin(story);
        when(stories.findByIdForUpdate(any())).thenReturn(Optional.of(story));
        when(inputs.findById(any())).thenReturn(Optional.of(input));
    }

    @Test
    void OK면_그대로_진행하고_기록하지_않는다() {
        ai.next = SafetyResult.ok();

        SafetyOutcome outcome = check("평범한 글");

        assertThat(outcome).isEqualTo(SafetyOutcome.proceed());
        assertThat(input.isToneRequired()).isFalse();
        verify(flags, never()).save(any());
    }

    @Test
    void SENSITIVE이고_tone이_없으면_분위기_선택() {
        ai.next = new SafetyResult(SafetyLevel.SENSITIVE, "loss", "무거운 주제예요");

        SafetyOutcome outcome = check("할머니가 돌아가셨어요");

        assertThat(outcome).isEqualTo(new SafetyOutcome(SafetyAction.CHOOSE_TONE, SafetyLevel.SENSITIVE, "loss",
                "무거운 주제예요", null));
        assertThat(input.isToneRequired()).isTrue();
        assertThat(story.getStatus()).isEqualTo(StoryStatus.DRAFT);
        verify(flags, never()).save(any());
    }

    @Test
    void SENSITIVE이고_tone을_이미_골랐으면_진행() {
        input.chooseTone(Tone.WARM);
        ai.next = new SafetyResult(SafetyLevel.SENSITIVE, "loss", "무거운 주제예요");

        SafetyOutcome outcome = check("할머니가 돌아가셨어요");

        assertThat(outcome.action()).isEqualTo(SafetyAction.PROCEED);
        assertThat(outcome.level()).isEqualTo(SafetyLevel.SENSITIVE);
        assertThat(input.isToneRequired()).isTrue();
    }

    @Test
    void REVISE_1번째는_다시_쓰기_안내() {
        reviseCountBefore(0);
        ai.next = revise();

        SafetyOutcome outcome = check("혼내 주고 싶어요");

        assertThat(outcome).isEqualTo(new SafetyOutcome(SafetyAction.ASK_REVISE, SafetyLevel.REVISE,
                "harmful_discipline", "다시 써 주세요", null));
        assertThat(story.getStatus()).isEqualTo(StoryStatus.DRAFT);
        SafetyFlag flag = savedFlag();
        assertThat(flag.getLevel()).isEqualTo(SafetyLevel.REVISE);
        assertThat(flag.getOccurrenceNo()).isOne();
        assertThat(flag.getSource()).isEqualTo(SafetySource.FORM);
        assertThat(flag.getCategory()).isEqualTo("harmful_discipline");
        assertThat(flag.getDetectedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void REVISE_2번째는_tone과_상관없이_차단하고_양육_상담(boolean toneChosen) {
        if (toneChosen) {
            input.chooseTone(Tone.PLAIN);
        }
        reviseCountBefore(1);
        ai.next = revise();

        SafetyOutcome outcome = check("혼내 주고 싶어요");

        assertThat(outcome.action()).isEqualTo(SafetyAction.BLOCK);
        assertThat(outcome.helpKind()).isEqualTo(HelpKind.PARENTING);
        assertThat(story.getStatus()).isEqualTo(StoryStatus.BLOCKED);
        assertThat(savedFlag().getOccurrenceNo()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5})
    void CRISIS는_REVISE_횟수와_상관없이_차단하고_아이_안전(int reviseCount) {
        reviseCountBefore(reviseCount);
        when(flags.countByStoryIdAndLevel(any(), eq(SafetyLevel.CRISIS))).thenReturn(0L);
        ai.next = new SafetyResult(SafetyLevel.CRISIS, "abuse_neglect", "아이의 안전이 걱정돼요");

        SafetyOutcome outcome = check("때렸어요");

        assertThat(outcome).isEqualTo(new SafetyOutcome(SafetyAction.BLOCK, SafetyLevel.CRISIS, "abuse_neglect",
                "아이의 안전이 걱정돼요", HelpKind.CHILD_SAFETY));
        assertThat(story.getStatus()).isEqualTo(StoryStatus.BLOCKED);
        SafetyFlag flag = savedFlag();
        assertThat(flag.getLevel()).isEqualTo(SafetyLevel.CRISIS);
        assertThat(flag.getOccurrenceNo()).isOne();
    }

    @Test
    void tone이_있어도_CRISIS는_차단() {
        input.chooseTone(Tone.WARM);
        ai.next = new SafetyResult(SafetyLevel.CRISIS, "self_harm", "걱정돼요");

        assertThat(check("글").action()).isEqualTo(SafetyAction.BLOCK);
    }

    @Test
    void 인터뷰_답변이면_차례를_기록하고_직전_질문을_보낸다() {
        reviseCountBefore(0);
        ai.next = revise();
        InterviewTurn turn = InterviewTurn.ask(story, 2, "그때 아이는 어땠나요?");

        guard.check(story, SafetySource.INTERVIEW, List.of("답변"), turn, "그때 아이는 어땠나요?");

        assertThat(ai.lastQuestionContext).isEqualTo("그때 아이는 어땠나요?");
        assertThat(ai.lastSource).isEqualTo(SafetySource.INTERVIEW);
        SafetyFlag flag = savedFlag();
        assertThat(flag.getInterviewTurn()).isSameAs(turn);
        assertThat(flag.getSource()).isEqualTo(SafetySource.INTERVIEW);
    }

    @Test
    void 빈_글은_빼고_보낸다() {
        ai.next = SafetyResult.ok();

        check("", null, "  ", "글");

        assertThat(ai.lastTexts).containsExactly("글");
    }

    @Test
    void 남은_글이_없으면_AI를_부르지_않고_진행() {
        SafetyOutcome outcome = check("", null, "   ");

        assertThat(outcome).isEqualTo(SafetyOutcome.proceed());
        assertThat(ai.calls).isZero();
        verify(stories, never()).findByIdForUpdate(any());
    }

    @Test
    void category가_비어_있으면_other로_기록한다() {
        reviseCountBefore(0);
        ai.next = new SafetyResult(SafetyLevel.REVISE, null, "다시 써 주세요");

        check("글");

        assertThat(savedFlag().getCategory()).isEqualTo(SafetyGuard.UNKNOWN_CATEGORY);
    }

    @Test
    void AI를_부르는_사이에_상태가_바뀌었으면_기록하지_않는다() {
        Story stale = story(user());
        stale.changeStatus(StoryStatus.INTERVIEWING);
        ai.next = revise();

        assertThatThrownBy(() -> guard.check(stale, SafetySource.FORM, List.of("글"), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        verify(flags, never()).save(any());
    }

    private SafetyOutcome check(String... texts) {
        return guard.check(story, SafetySource.FORM, Arrays.asList(texts), null, null);
    }

    private void reviseCountBefore(int count) {
        when(flags.countByStoryIdAndLevel(any(), eq(SafetyLevel.REVISE))).thenReturn((long) count);
    }

    private static SafetyResult revise() {
        return new SafetyResult(SafetyLevel.REVISE, "harmful_discipline", "다시 써 주세요");
    }

    private SafetyFlag savedFlag() {
        ArgumentCaptor<SafetyFlag> captor = ArgumentCaptor.forClass(SafetyFlag.class);
        verify(flags).save(captor.capture());
        return captor.getValue();
    }

    static class StubAiClient implements AiClient {

        SafetyResult next = SafetyResult.ok();
        int calls;
        SafetySource lastSource;
        List<String> lastTexts = new ArrayList<>();
        String lastQuestionContext;

        @Override
        public SafetyResult checkSafety(SafetySource source, List<String> texts, String questionContext) {
            calls++;
            lastSource = source;
            lastTexts = texts;
            lastQuestionContext = questionContext;
            return next;
        }

        @Override
        public InterviewReply startInterview(String threadId, UUID runId, Map<String, Object> context, Tone tone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InterviewReply answerInterview(String threadId, String answer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void generate(String threadId, UUID runId, int runNo, String outputPrefix, Tone tone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiGenerationStatus status(String threadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiGenerationResult result(String threadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void approve(String threadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void regenerate(String threadId, RegenerateScope scope, List<Integer> pages, String feedback,
                               String baseCheckpointId, UUID runId, int runNo, String outputPrefix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void retry(String threadId, UUID runId, int runNo, String outputPrefix) {
            throw new UnsupportedOperationException();
        }
    }
}
