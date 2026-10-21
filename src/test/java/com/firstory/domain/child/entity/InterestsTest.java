package com.firstory.domain.child.entity;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.firstory.domain.child.entity.InterestCode.ANIMAL;
import static com.firstory.domain.child.entity.InterestCode.DINOSAUR;
import static com.firstory.domain.child.entity.InterestCode.SPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterestsTest {

    @Test
    void 목록_3개와_직접_입력_2개는_통과() {
        Interests interests = Interests.normalize(List.of(DINOSAUR, SPACE, ANIMAL), List.of("기차", "파란색"));

        assertThat(interests.codes()).hasSize(3);
        assertThat(interests.customs()).hasSize(2);
    }

    @Test
    void 목록_3개와_직접_입력_3개면_예외() {
        assertThatThrownBy(() -> Interests.normalize(List.of(DINOSAUR, SPACE, ANIMAL), List.of("기차", "파란색", "블록")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INTERESTS_LIMIT_EXCEEDED);
    }

    @Test
    void 직접_입력은_20자까지_통과하고_21자면_예외() {
        assertThat(Interests.normalize(null, List.of("가".repeat(20))).customs()).hasSize(1);

        assertThatThrownBy(() -> Interests.normalize(null, List.of("가".repeat(21))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("직접 입력은 20자까지 쓸 수 있어요.")
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_VALUE_TOO_LONG);
    }

    @Test
    void 직접_입력은_공백과_중복을_정리하고_순서는_유지한다() {
        Interests interests = Interests.normalize(null, Arrays.asList(" 기차 ", "", "   ", null, "공", "기차", "공 "));

        assertThat(interests.customs()).containsExactly("기차", "공");
    }

    @Test
    void 목록은_중복을_지우고_선언_순서로_정렬한다() {
        Interests interests = Interests.normalize(Arrays.asList(ANIMAL, DINOSAUR, null, ANIMAL), null);

        assertThat(interests.codes()).containsExactly(DINOSAUR, ANIMAL);
    }

    @Test
    void 개수는_정리한_뒤에_센다() {
        Interests interests = Interests.normalize(List.of(DINOSAUR, DINOSAUR), List.of("a", "b", "c", "d", "a ", " "));

        assertThat(interests.codes().size() + interests.customs().size()).isEqualTo(5);
    }

    @Test
    void null이면_빈_목록() {
        Interests interests = Interests.normalize(null, null);

        assertThat(interests.codes()).isEmpty();
        assertThat(interests.customs()).isEmpty();
    }
}
