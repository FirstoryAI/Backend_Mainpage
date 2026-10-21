package com.firstory.domain.child.entity;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static com.firstory.support.Fixtures.child;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChildTest {

    @Test
    void 호칭은_앞뒤_공백을_지우고_저장한다() {
        Child child = child(user(), "아이 A");

        child.rename("  우리 첫째  ");

        assertThat(child.getDisplayName()).isEqualTo("우리 첫째");
    }

    @Test
    void 호칭은_30자까지_가능하다() {
        Child child = child(user(), "아이 A");

        child.rename("가".repeat(30));

        assertThat(child.getDisplayName()).hasSize(30);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 호칭이_비었으면_예외(String name) {
        assertInvalidRename(name);
    }

    @Test
    void 호칭이_30자를_넘으면_예외() {
        assertInvalidRename("가".repeat(31));
    }

    private static void assertInvalidRename(String name) {
        Child child = child(user(), "아이 A");

        assertThatThrownBy(() -> child.rename(name))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CHILD_NAME);
        assertThat(child.getDisplayName()).isEqualTo("아이 A");
    }

    @Test
    void 성향에_OTHER가_있으면_직접_입력이_필요하다() {
        Child child = child(user(), "아이 A");

        for (String blank : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> child.updateProfile(YearMonth.of(2021, 3), Gender.FEMALE,
                    Set.of(Trait.SHY, Trait.OTHER), blank, Interests.empty()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_VALUE_REQUIRED);
        }
    }

    @Test
    void 성향에_OTHER가_있으면_직접_입력을_공백을_지워_저장한다() {
        Child child = child(user(), "아이 A");

        child.updateProfile(YearMonth.of(2021, 3), Gender.FEMALE, Set.of(Trait.OTHER), " 겁이 많아요 ",
                Interests.empty());

        assertThat(child.getCustomTrait()).isEqualTo("겁이 많아요");
    }

    @Test
    void 성향에_OTHER가_없으면_직접_입력은_버린다() {
        Child child = child(user(), "아이 A");

        child.updateProfile(YearMonth.of(2021, 3), Gender.FEMALE, Set.of(Trait.SHY), "겁이 많아요", Interests.empty());

        assertThat(child.getCustomTrait()).isNull();
    }

    @Test
    void 성향_직접_입력은_20자까지() {
        Child child = child(user(), "아이 A");
        child.updateProfile(YearMonth.of(2021, 3), Gender.FEMALE, Set.of(Trait.OTHER), "가".repeat(20),
                Interests.empty());

        assertThatThrownBy(() -> child.updateProfile(YearMonth.of(2021, 3), Gender.FEMALE, Set.of(Trait.OTHER),
                "가".repeat(21), Interests.empty()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("직접 입력은 20자까지 쓸 수 있어요.")
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_VALUE_TOO_LONG);
    }

    @Test
    void 좋아하는_것은_목록과_직접_입력을_나눠_저장한다() {
        Child child = child(user(), "아이 A");

        child.updateProfile(YearMonth.of(2021, 3), Gender.FEMALE, Set.of(),
                null, new Interests(List.of(InterestCode.SPACE, InterestCode.DINOSAUR), List.of(" 기차 ")));

        assertThat(child.getInterestCodes()).containsExactly(InterestCode.DINOSAUR, InterestCode.SPACE);
        assertThat(child.getCustomInterests()).containsExactly("기차");
    }

    @Test
    void 만들_때도_호칭을_검증한다() {
        assertThatThrownBy(() -> child(user(), " "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CHILD_NAME);
    }
}
