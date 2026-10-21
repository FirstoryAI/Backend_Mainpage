package com.firstory.child.domain;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterestsTest {

    @Test
    void 다섯_개까지_통과() {
        assertThat(Interests.normalize(List.of("a", "b", "c", "d", "e"))).hasSize(5);
    }

    @Test
    void 여섯_개면_예외() {
        assertThatThrownBy(() -> Interests.normalize(List.of("a", "b", "c", "d", "e", "f")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INTERESTS_LIMIT_EXCEEDED);
    }

    @Test
    void 스무_자까지_통과하고_스물한_자면_예외() {
        assertThat(Interests.normalize(List.of("가".repeat(20)))).hasSize(1);

        assertThatThrownBy(() -> Interests.normalize(List.of("가".repeat(21))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INTEREST_TOO_LONG);
    }

    @Test
    void 공백과_중복을_정리하고_순서는_유지한다() {
        List<String> result = Interests.normalize(Arrays.asList(" 공룡 ", "", "   ", null, "기차", "공룡", "기차 "));

        assertThat(result).containsExactly("공룡", "기차");
    }

    @Test
    void 정리_후_다섯_개면_통과() {
        assertThat(Interests.normalize(List.of("a", "b", "c", "d", "e", "a ", " "))).hasSize(5);
    }

    @Test
    void null이면_빈_리스트() {
        assertThat(Interests.normalize(null)).isEmpty();
    }
}
