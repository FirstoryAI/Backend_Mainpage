package com.firstory.domain.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고정 인증번호는 local 프로필에서만 읽고, 다른 프로필에 설정하면 무시하지 않고 기동을 실패시킨다.
 */
class LocalFixedVerificationCodeTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocalFixedVerificationCode.class, LocalFixedVerificationCode.Guard.class);

    @Test
    void local이면_고정_인증번호를_쓴다() {
        runner.withPropertyValues("spring.profiles.active=local", "firstory.sms.local-fixed-code=000000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LocalFixedVerificationCode.class).code()).contains("000000");
                });
    }

    @Test
    void local이어도_비어_있으면_무작위() {
        runner.withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context.getBean(LocalFixedVerificationCode.class).code()).isEmpty());
    }

    @Test
    void local이어도_숫자_6자리가_아니면_기동_실패() {
        runner.withPropertyValues("spring.profiles.active=local", "firstory.sms.local-fixed-code=0000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void local이_아닌_프로필에_설정하면_기동_실패() {
        runner.withPropertyValues("spring.profiles.active=test", "firstory.sms.local-fixed-code=000000")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("local 프로필에서만");
                });
        runner.withPropertyValues("firstory.sms.local-fixed-code=000000")
                .run(context -> assertThat(context).as("프로필 없음(운영)").hasFailed());
    }

    @Test
    void local이_아닌_프로필에_설정이_없으면_빈이_없다() {
        runner.withPropertyValues("spring.profiles.active=test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(LocalFixedVerificationCode.class);
                });
    }
}
