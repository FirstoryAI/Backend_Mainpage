package com.firstory.domain.story.dto;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.InterestCode;
import com.firstory.domain.child.entity.Trait;
import com.firstory.domain.story.entity.ChildSnapshot;
import com.firstory.domain.story.entity.Emotion;
import com.firstory.domain.story.entity.InterviewEndReason;
import com.firstory.domain.story.entity.InterviewTurn;
import com.firstory.domain.story.entity.MessageDirection;
import com.firstory.domain.story.entity.MomentReaction;
import com.firstory.domain.story.entity.SituationCategory;
import com.firstory.domain.story.entity.SituationTiming;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryInput;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.entity.Tone;

import java.util.List;

/**
 * 이어서 만들기와 생성 전 확인(CRT-06) 화면용. 지금까지의 입력 전체와 인터뷰 질문·답변.
 * 호칭은 부모 화면용으로만 넣는다. 회원 정보(휴대폰 등)는 없다.
 *
 * @param situation 아직 입력하지 않았으면 null
 * @param child     아직 입력하지 않았으면 null
 */
public record StoryDraftResponse(Long storyId, StoryStatus status, Tone tone, boolean toneRequired,
                                 Situation situation, ChildInfo child, Parent parent, Interview interview) {

    public record Situation(SituationTiming timing, SituationCategory category, String customCategory,
                            String description, List<MomentReaction> reactions, String customReaction,
                            List<Emotion> emotions) {
    }

    /**
     * @param childId     저장된 프로필에 연결됐으면 id. 저장하지 않았으면 null
     * @param displayName 프로필의 호칭. 저장하지 않았으면 null
     */
    public record ChildInfo(Long childId, String displayName, int ageMonths, Gender gender, List<Trait> traits,
                            String customTrait, List<InterestCode> interestCodes, List<String> customInterests) {
    }

    public record Parent(String response, String concern, String message, MessageDirection messageDirection) {
    }

    /**
     * @param summary   인터뷰가 끝났으면 요약. 아니면 null
     * @param endReason 인터뷰가 끝났으면 끝난 이유. 아니면 null
     */
    public record Interview(List<Turn> turns, String summary, InterviewEndReason endReason) {
    }

    public record Turn(int turnNo, String question, String answer) {
    }

    /**
     * story.child를 읽으므로 트랜잭션 안에서 부른다.
     */
    public static StoryDraftResponse of(Story story, StoryInput input, List<InterviewTurn> turns) {
        Situation situation = input.hasSituation()
                ? new Situation(input.getSituationTiming(), input.getSituationCategory(),
                input.getSituationCustomCategory(), input.getDescription(), List.copyOf(input.getMomentReactions()),
                input.getMomentCustomReaction(), List.copyOf(input.getEmotions()))
                : null;
        return new StoryDraftResponse(story.getId(), story.getStatus(), input.getTone(), input.isToneRequired(),
                situation, childInfo(story.getChild(), input.getChildSnapshot()),
                new Parent(input.getParentResponse(), input.getParentConcern(), input.getParentMessage(),
                        input.getMessageDirection()),
                new Interview(turns.stream().map(t -> new Turn(t.getTurnNo(), t.getQuestion(), t.getAnswer())).toList(),
                        input.getInterviewSummary(), input.getInterviewEndReason()));
    }

    private static ChildInfo childInfo(Child child, ChildSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new ChildInfo(child == null ? null : child.getId(), child == null ? null : child.getDisplayName(),
                snapshot.ageMonths(), snapshot.gender(), snapshot.traits(), snapshot.customTrait(),
                snapshot.interestCodes(), snapshot.customInterests());
    }

    // 부모 입력과 아이 정보가 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "StoryDraftResponse[storyId=" + storyId + ", status=" + status + "]";
    }
}
