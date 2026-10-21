package com.firstory.domain.child.entity;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChildLabelPolicyTest {

    @Test
    void 비어_있으면_아이_A() {
        assertThat(ChildLabelPolicy.nextLabel(List.of())).isEqualTo("아이 A");
    }

    @Test
    void A와_B가_있으면_C() {
        assertThat(ChildLabelPolicy.nextLabel(List.of("아이 A", "아이 B"))).isEqualTo("아이 C");
    }

    @Test
    void A가_비어_있으면_다시_A() {
        assertThat(ChildLabelPolicy.nextLabel(List.of("아이 B"))).isEqualTo("아이 A");
    }

    @Test
    void 부모가_바꾼_호칭은_자리를_차지하지_않는다() {
        assertThat(ChildLabelPolicy.nextLabel(List.of("첫째", "아이 A"))).isEqualTo("아이 B");
    }

    @Test
    void A부터_Z까지_모두_쓰면_예외() {
        List<String> all = IntStream.rangeClosed('A', 'Z').mapToObj(c -> "아이 " + (char) c).toList();

        assertThatThrownBy(() -> ChildLabelPolicy.nextLabel(all))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHILD_LABEL_EXHAUSTED);
    }
}
