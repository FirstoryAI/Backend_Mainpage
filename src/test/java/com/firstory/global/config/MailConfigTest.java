package com.firstory.global.config;

import com.firstory.global.config.FirstoryProperties.Mail;
import com.firstory.global.config.FirstoryProperties.Mail.MailMode;
import com.firstory.global.mail.EmailMask;
import com.firstory.global.mail.LoggingEmailSender;
import com.firstory.global.mail.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailConfigTest {

    final MailConfig config = new MailConfig();

    @Test
    void log는_local_test_dev에서만_쓸_수_있다() {
        FirstoryProperties log = properties(new Mail(MailMode.LOG, null, 0, null, null, null));

        assertThat(config.emailSender(log, profile("dev"))).isInstanceOf(LoggingEmailSender.class);
        assertThat(config.emailSender(log, profile("local"))).isInstanceOf(LoggingEmailSender.class);
        assertThatThrownBy(() -> config.emailSender(log, profile("prod"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.emailSender(log, new MockEnvironment())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void smtp는_계정과_앱_비밀번호가_있어야_하고_기본은_Gmail_587이다() {
        assertThatThrownBy(() -> new Mail(MailMode.SMTP, null, 0, "team@gmail.com", " ", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_PASSWORD");
        assertThatThrownBy(() -> new Mail(null, null, 0, null, null, null)).isInstanceOf(IllegalStateException.class);

        Mail smtp = new Mail(MailMode.SMTP, "", 0, "team@gmail.com", "abcd efgh ijkl mnop", "");
        assertThat(smtp.host()).isEqualTo("smtp.gmail.com");
        assertThat(smtp.port()).isEqualTo(587);
        assertThat(smtp.from()).isEqualTo("team@gmail.com");
        assertThat(config.emailSender(properties(smtp), profile("prod"))).isInstanceOf(SmtpEmailSender.class);
    }

    @Test
    void 로그에_남기는_이메일은_가린다() {
        assertThat(EmailMask.mask("parent@example.com")).isEqualTo("p*****@example.com");
        assertThat(EmailMask.mask("a@b.com")).isEqualTo("a*@b.com");
        assertThat(EmailMask.mask("broken")).isEqualTo("***");
    }

    private static FirstoryProperties properties(Mail mail) {
        return new FirstoryProperties(null, null, null, null, null, null, null, mail, null);
    }

    private static MockEnvironment profile(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
