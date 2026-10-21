package com.firstory.domain.user.entity;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.firstory.support.Fixtures.NOW;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void 네_번_실패하면_잠기지_않는다() {
        User user = user();
        failTimes(user, 4);

        assertThat(user.isLocked(NOW)).isFalse();
        assertThat(user.getFailedLoginCount()).isEqualTo(4);
    }

    @Test
    void 다섯_번째_실패에서_15분_잠기고_횟수는_0이_된다() {
        User user = user();
        failTimes(user, 5);

        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)).minusSeconds(1))).isTrue();
        assertThat(user.getFailedLoginCount()).isZero();
    }

    @Test
    void 잠기고_15분이_지나면_풀린다() {
        User user = user();
        failTimes(user, 5);

        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void 성공하면_실패_횟수와_잠금을_초기화한다() {
        User user = user();
        failTimes(user, 3);
        user.recordLoginSuccess();
        assertThat(user.getFailedLoginCount()).isZero();

        failTimes(user, 5);
        user.recordLoginSuccess();
        assertThat(user.isLocked(NOW)).isFalse();
        assertThat(user.getLockedUntil()).isNull();
    }

    private static void failTimes(User user, int times) {
        for (int i = 0; i < times; i++) {
            user.recordLoginFailure(NOW);
        }
    }
}
