package com.firstory.domain.story.entity;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.InterestCode;
import com.firstory.domain.child.entity.Interests;
import com.firstory.domain.child.entity.Trait;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.firstory.support.Fixtures.child;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

class ChildSnapshotTest {

    @Test
    void 아이_호칭을_담지_않는다() {
        List<String> components = Arrays.stream(ChildSnapshot.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components).containsExactly("ageMonths", "gender", "traits", "customTrait", "interestCodes",
                "customInterests");
        assertThat(components).noneMatch(name -> name.toLowerCase().contains("name"));
    }

    @Test
    void 저장된_프로필에서_만든다() {
        Child child = child(user(), "우리 첫째");

        ChildSnapshot snapshot = ChildSnapshot.of(child, LocalDate.of(2026, 10, 21));

        assertThat(snapshot.ageMonths()).isEqualTo(67);
        assertThat(snapshot.gender()).isEqualTo(Gender.FEMALE);
        assertThat(snapshot.traits()).containsExactly(Trait.SHY, Trait.CURIOUS);
        assertThat(snapshot.interestCodes()).containsExactly(InterestCode.DINOSAUR);
        assertThat(snapshot.customInterests()).containsExactly("기차");
        assertThat(snapshot.toString()).doesNotContain("우리 첫째");
    }

    @Test
    void 저장하지_않은_입력은_관심사와_성향_직접_입력을_정규화한다() {
        ChildSnapshot snapshot = ChildSnapshot.of(YearMonth.of(2022, 1), Gender.UNSPECIFIED,
                Set.of(Trait.ACTIVE), "버려져요", new Interests(List.of(InterestCode.SPACE), List.of(" 공 ", "공")),
                LocalDate.of(2026, 1, 31));

        assertThat(snapshot.ageMonths()).isEqualTo(48);
        assertThat(snapshot.customTrait()).isNull();
        assertThat(snapshot.interestCodes()).containsExactly(InterestCode.SPACE);
        assertThat(snapshot.customInterests()).containsExactly("공");
    }
}
