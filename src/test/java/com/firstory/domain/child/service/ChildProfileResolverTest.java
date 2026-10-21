package com.firstory.domain.child.service;

import com.firstory.domain.child.dto.ChildProfile;
import com.firstory.domain.child.dto.ChildResponse;
import com.firstory.domain.child.dto.ChildSelection;
import com.firstory.domain.child.dto.ResolvedChild;
import com.firstory.domain.child.entity.Gender;
import com.firstory.domain.child.entity.InterestCode;
import com.firstory.domain.child.entity.Trait;
import com.firstory.global.config.ClockConfig;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import com.firstory.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChildProfileResolverTest extends IntegrationTest {

    static final ChildProfile SAVED = new ChildProfile(YearMonth.of(2021, 3), Gender.FEMALE,
            Set.of(Trait.SHY), null, List.of(InterestCode.DINOSAUR), List.of());
    static final ChildProfile EDITED = new ChildProfile(YearMonth.of(2020, 5), Gender.MALE,
            Set.of(Trait.CURIOUS, Trait.ACTIVE, Trait.OTHER), " 겁이 많아요 ", List.of(InterestCode.SPACE),
            List.of(" 기차 ", "기차", "블록"));

    @Autowired
    ChildProfileResolver resolver;

    @Autowired
    ChildService childService;

    @Autowired
    JsonMapper jsonMapper;

    Long userId;

    @BeforeEach
    void signUpParent() throws Exception {
        userId = userId(signUp(newEmail(), newPhone()));
    }

    @Test
    void _1_새로_입력하고_체크하면_새_프로필을_저장하고_연결한다() {
        ResolvedChild resolved = resolver.resolve(userId, new ChildSelection(null, EDITED, true));

        assertThat(resolved.child()).isNotNull();
        ChildResponse saved = childService.get(userId, resolved.child().getId());
        assertThat(saved.displayName()).isEqualTo("아이 A");
        assertThat(saved.birthYm()).isEqualTo("2020-05");
        assertThat(saved.customTrait()).isEqualTo("겁이 많아요");
        assertThat(saved.interestCodes()).containsExactly(InterestCode.SPACE);
        assertThat(saved.customInterests()).containsExactly("기차", "블록");
        assertSnapshotIs(resolved, EDITED);
    }

    @Test
    void _2_새로_입력하고_체크하지_않으면_저장하지_않는다() {
        ResolvedChild resolved = resolver.resolve(userId, new ChildSelection(null, EDITED, false));

        assertThat(resolved.child()).isNull();
        assertThat(childService.list(userId)).isEmpty();
        assertSnapshotIs(resolved, EDITED);
    }

    @Test
    void _3_불러온_프로필을_고치고_체크하면_프로필을_갱신한다() {
        Long childId = childService.create(userId, SAVED).id();

        ResolvedChild resolved = resolver.resolve(userId, new ChildSelection(childId, EDITED, true));

        assertThat(resolved.child().getId()).isEqualTo(childId);
        ChildResponse updated = childService.get(userId, childId);
        assertThat(updated.birthYm()).isEqualTo("2020-05");
        assertThat(updated.gender()).isEqualTo(Gender.MALE);
        assertThat(updated.traits()).containsExactly(Trait.ACTIVE, Trait.CURIOUS, Trait.OTHER);
        assertThat(updated.customTrait()).isEqualTo("겁이 많아요");
        assertThat(updated.interestCodes()).containsExactly(InterestCode.SPACE);
        assertThat(updated.customInterests()).containsExactly("기차", "블록");
        assertSnapshotIs(resolved, EDITED);
    }

    @Test
    void _3_값이_같으면_프로필을_건드리지_않는다() {
        Long childId = childService.create(userId, SAVED).id();
        Timestamp before = updatedAt(childId);
        advance(Duration.ofMinutes(1));

        resolver.resolve(userId, new ChildSelection(childId, SAVED, true));

        assertThat(updatedAt(childId)).isEqualTo(before);
    }

    @Test
    void _4_불러온_프로필을_고쳤지만_체크를_풀면_프로필은_그대로다() {
        Long childId = childService.create(userId, SAVED).id();

        ResolvedChild resolved = resolver.resolve(userId, new ChildSelection(childId, EDITED, false));

        assertThat(resolved.child().getId()).isEqualTo(childId);
        // DB에서 다시 읽어 그대로인지 확인한다
        ChildResponse reloaded = childService.get(userId, childId);
        assertThat(reloaded.birthYm()).isEqualTo("2021-03");
        assertThat(reloaded.gender()).isEqualTo(Gender.FEMALE);
        assertThat(reloaded.traits()).containsExactly(Trait.SHY);
        assertThat(reloaded.customTrait()).isNull();
        assertThat(reloaded.interestCodes()).containsExactly(InterestCode.DINOSAUR);
        assertThat(reloaded.customInterests()).isEmpty();
        assertSnapshotIs(resolved, EDITED);
    }

    @Test
    void _5_다른_회원의_아이면_CHILD_NOT_FOUND() throws Exception {
        Long otherUserId = userId(signUp(newEmail(), newPhone()));
        Long othersChild = childService.create(otherUserId, SAVED).id();

        for (boolean save : new boolean[]{true, false}) {
            assertThatThrownBy(() -> resolver.resolve(userId, new ChildSelection(othersChild, EDITED, save)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CHILD_NOT_FOUND);
        }
        assertThat(childService.get(otherUserId, othersChild).birthYm()).isEqualTo("2021-03");
    }

    @Test
    void 저장하지_않아도_입력값은_항상_검증한다() {
        ChildProfile future = new ChildProfile(YearMonth.of(2099, 1), Gender.MALE, Set.of(), null, List.of(),
                List.of());

        assertThatThrownBy(() -> resolver.resolve(userId, new ChildSelection(null, future, false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BIRTH_YM);
    }

    @Test
    void snapshot에는_호칭이_없다() {
        Long childId = childService.create(userId, SAVED).id();
        childService.rename(userId, childId, "우리첫째");

        ResolvedChild resolved = resolver.resolve(userId, new ChildSelection(childId, SAVED, true));

        String json = jsonMapper.writeValueAsString(resolved.snapshot());
        assertThat(json).doesNotContain("우리첫째").doesNotContain("아이 A").doesNotContain("displayName");
    }

    // snapshot은 프로필이 아니라 화면의 최종 입력값으로 만든다
    private void assertSnapshotIs(ResolvedChild resolved, ChildProfile input) {
        assertThat(resolved.snapshot().ageMonths())
                .isEqualTo((int) ChronoUnit.MONTHS.between(input.birthYm(), YearMonth.from(ClockConfig.today(clock))));
        assertThat(resolved.snapshot().gender()).isEqualTo(input.gender());
        assertThat(resolved.snapshot().traits()).containsExactlyElementsOf(input.traits().stream().sorted().toList());
        assertThat(resolved.snapshot().customTrait()).isEqualTo(input.customTrait());
        assertThat(resolved.snapshot().interestCodes()).containsExactlyElementsOf(input.interestCodes());
        assertThat(resolved.snapshot().customInterests()).containsExactlyElementsOf(input.customInterests());
    }

    private Timestamp updatedAt(Long childId) {
        return jdbc.queryForObject("SELECT updated_at FROM children WHERE id = ?", Timestamp.class, childId);
    }
}
