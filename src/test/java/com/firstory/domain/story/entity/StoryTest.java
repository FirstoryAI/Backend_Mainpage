package com.firstory.domain.story.entity;

import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.user.entity.User;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static com.firstory.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryTest {

    private final User user = user();

    @Test
    void 새_동화는_DRAFT() {
        assertThat(story(user).getStatus()).isEqualTo(StoryStatus.DRAFT);
    }

    @Test
    void 생성_진행은_허용된_순서를_차례로_밟는다() {
        Story story = requested();

        story.advanceGenerationTo(StoryStatus.REVIEWING);

        assertThat(story.getStatus()).isEqualTo(StoryStatus.REVIEWING);
    }

    @Test
    void 그림만_다시는_REQUESTED에서_GENERATING_IMAGES로_바로_간다() {
        Story story = requested();

        story.advanceGenerationTo(StoryStatus.GENERATING_IMAGES);

        assertThat(story.getStatus()).isEqualTo(StoryStatus.GENERATING_IMAGES);
    }

    @Test
    void 생성_진행은_뒤로_가지_않는다() {
        Story story = requested();
        story.advanceGenerationTo(StoryStatus.GENERATING_IMAGES);

        story.advanceGenerationTo(StoryStatus.GENERATING_TEXT);
        story.advanceGenerationTo(StoryStatus.GENERATING_IMAGES);

        assertThat(story.getStatus()).isEqualTo(StoryStatus.GENERATING_IMAGES);
    }

    @Test
    void 생성_중이_아니면_생성_진행으로_옮길_수_없다() {
        Story story = story(user);

        assertThatThrownBy(() -> story.advanceGenerationTo(StoryStatus.GENERATING_TEXT))
                .isInstanceOf(BusinessException.class);
    }

    private Story requested() {
        Story story = story(user);
        story.changeStatus(StoryStatus.INTERVIEWING);
        story.changeStatus(StoryStatus.CONFIRMING);
        story.changeStatus(StoryStatus.REQUESTED);
        return story;
    }

    @Test
    void 허용되지_않은_전이는_예외() {
        Story story = story(user);

        assertThatThrownBy(() -> story.changeStatus(StoryStatus.REVIEWING))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        assertThat(story.getStatus()).isEqualTo(StoryStatus.DRAFT);
    }

    @Test
    void 다른_동화의_버전은_보여줄_수_없다() {
        Story story = story(user);
        moveToReviewing(story);
        Story other = story(user);
        StoryVersion otherVersion = version(other, run(other, 1), 1, null);

        assertThatThrownBy(() -> story.showVersion(otherVersion))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VERSION_NOT_IN_STORY);
    }

    @Test
    void REVIEWING이_아니면_버전을_바꿀_수_없다() {
        Story story = story(user);
        StoryVersion v1 = version(story, run(story, 1), 1, null);

        assertThatThrownBy(() -> story.showVersion(v1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void 버전을_앞뒤로_오가도_다시_만들기_횟수는_그대로다() {
        Story story = story(user);
        moveToReviewing(story);
        StoryVersion v1 = version(story, run(story, 1), 1, null);
        StoryVersion v2 = version(story, run(story, 2), 2, v1);

        story.showVersion(v1);
        story.showVersion(v2);
        story.showVersion(v1);
        story.showVersion(v2);

        assertThat(story.getCurrentVersion()).isSameAs(v2);
        assertThat(story.getTitle()).isEqualTo("제목 v2");
        assertThat(story.getRegenerateCount()).isZero();
    }

    @Test
    void 다시_만들기는_한도까지만_가능하다() {
        Story story = story(user);
        moveToReviewing(story);

        for (int i = 1; i <= 3; i++) {
            story.requestRegeneration(3);
            assertThat(story.getStatus()).isEqualTo(StoryStatus.REQUESTED);
            assertThat(story.getRegenerateCount()).isEqualTo(i);
            story.changeStatus(StoryStatus.GENERATING_TEXT);
            story.changeStatus(StoryStatus.GENERATING_IMAGES);
            story.changeStatus(StoryStatus.REVIEWING);
        }

        assertThatThrownBy(() -> story.requestRegeneration(3))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REGENERATION_LIMIT_REACHED);
        assertThat(story.getStatus()).isEqualTo(StoryStatus.REVIEWING);
    }

    @Test
    void 확인_중이_아니면_다시_만들기를_할_수_없다() {
        Story story = story(user);

        assertThatThrownBy(() -> story.requestRegeneration(3))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void 실패한_동화는_다시_시도할_수_있고_다시_만들기_횟수는_그대로다() {
        Story story = story(user);
        moveToReviewing(story);
        story.requestRegeneration(3);
        story.changeStatus(StoryStatus.FAILED);

        story.retry();

        assertThat(story.getStatus()).isEqualTo(StoryStatus.REQUESTED);
        assertThat(story.getRegenerateCount()).isEqualTo(1);
    }

    @Test
    void 다시_만들기_한도를_다_써도_실패_재시도는_가능하다() {
        Story story = story(user);
        moveToReviewing(story);
        story.requestRegeneration(1);
        story.changeStatus(StoryStatus.FAILED);

        story.retry();

        assertThat(story.getStatus()).isEqualTo(StoryStatus.REQUESTED);
        assertThat(story.getRegenerateCount()).isEqualTo(1);
    }

    @Test
    void 실패_상태가_아니면_다시_시도할_수_없다() {
        Story story = story(user);
        moveToReviewing(story);

        assertThatThrownBy(story::retry)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        assertThat(story.getStatus()).isEqualTo(StoryStatus.REVIEWING);
    }

    @Test
    void 확정하면_COMPLETED와_확정_시각이_남는다() {
        Story story = story(user);
        moveToReviewing(story);

        story.confirm(NOW);

        assertThat(story.getStatus()).isEqualTo(StoryStatus.COMPLETED);
        assertThat(story.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void 처음_읽은_시각은_한_번만_기록한다() {
        Story story = story(user);

        story.markFirstReadCompleted(NOW);
        story.markFirstReadCompleted(NOW.plusSeconds(60));

        assertThat(story.getFirstReadCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void 서재_라벨() {
        assertThat(labelAt(StoryStatus.DRAFT)).isEqualTo("만들던 이야기");
        assertThat(labelAt(StoryStatus.INTERVIEWING)).isEqualTo("만들던 이야기");
        assertThat(labelAt(StoryStatus.CONFIRMING)).isEqualTo("만들던 이야기");
        assertThat(labelAt(StoryStatus.REQUESTED)).isEqualTo("만드는 중");
        assertThat(labelAt(StoryStatus.GENERATING_TEXT)).isEqualTo("만드는 중");
        assertThat(labelAt(StoryStatus.GENERATING_IMAGES)).isEqualTo("만드는 중");
        assertThat(labelAt(StoryStatus.REVIEWING)).isEqualTo("확인을 기다려요");
        assertThat(labelAt(StoryStatus.FAILED)).isEqualTo("만들지 못했어요");
        assertThat(labelAt(StoryStatus.BLOCKED)).isEqualTo("만들 수 없는 이야기");

        Story completed = story(user);
        moveToReviewing(completed);
        completed.confirm(NOW);
        assertThat(completed.libraryLabel()).isEqualTo("아직 안 읽었어요");
        completed.markFirstReadCompleted(NOW);
        assertThat(completed.libraryLabel()).isEqualTo("함께 읽었어요");
    }

    private String labelAt(StoryStatus target) {
        Story story = story(user);
        switch (target) {
            case DRAFT -> { }
            case BLOCKED -> story.block();
            case FAILED -> {
                story.changeStatus(StoryStatus.INTERVIEWING);
                story.changeStatus(StoryStatus.CONFIRMING);
                story.changeStatus(StoryStatus.REQUESTED);
                story.changeStatus(StoryStatus.FAILED);
            }
            default -> {
                StoryStatus[] path = {StoryStatus.INTERVIEWING, StoryStatus.CONFIRMING, StoryStatus.REQUESTED,
                        StoryStatus.GENERATING_TEXT, StoryStatus.GENERATING_IMAGES, StoryStatus.REVIEWING};
                for (StoryStatus next : path) {
                    story.changeStatus(next);
                    if (next == target) {
                        break;
                    }
                }
            }
        }
        assertThat(story.getStatus()).isEqualTo(target);
        return story.libraryLabel();
    }
}
