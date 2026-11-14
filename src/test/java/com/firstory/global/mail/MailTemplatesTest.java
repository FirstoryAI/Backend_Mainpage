package com.firstory.global.mail;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MailTemplatesTest {

    private final MailTemplates templates = new MailTemplates("https://api.test/mail/", "https://app.test/");

    @Test
    void 인증번호_메일에_번호와_로고_캐릭터_주소가_들어가고_빈칸이_남지_않는다() {
        String html = templates.html(MailTemplates.VERIFY_SIGNUP, Map.of("code", "482913"));

        assertThat(html).contains("482913", "회원가입 인증번호예요", "https://api.test/mail/logo.png",
                "https://api.test/mail/mascot-verify.png").doesNotContain("{{", "}}", "mail//");
    }

    @Test
    void 비밀번호_찾기는_가입과_다른_문구다() {
        String html = templates.html(MailTemplates.VERIFY_PASSWORD_RESET, Map.of("code", "105726"));

        assertThat(html).contains("105726", "비밀번호 재설정 인증번호예요").doesNotContain("회원가입");
    }

    @Test
    void 알림_메일_버튼은_앱_주소로_간다() {
        assertThat(templates.html(MailTemplates.STORY_STARTED, Map.of())).contains("href=\"https://app.test/home\"");
        assertThat(templates.html(MailTemplates.STORY_COMPLETED, Map.of())).contains("href=\"https://app.test/library\"");
        assertThat(templates.html(MailTemplates.STORY_FAILED, Map.of())).contains("href=\"https://app.test/library\"");
    }

    @Test
    void 모든_메일_맨_아래에_문의_이메일과_인스타그램이_있다() {
        for (String name : new String[]{MailTemplates.VERIFY_SIGNUP, MailTemplates.VERIFY_PASSWORD_RESET,
                MailTemplates.STORY_STARTED, MailTemplates.STORY_COMPLETED, MailTemplates.STORY_FAILED}) {
            assertThat(templates.html(name, Map.of("code", "1"))).as(name)
                    .contains("mailto:firstory.official@gmail.com", "https://www.instagram.com/firstory.official/",
                            "@firstory.official");
        }
        assertThat(MailTemplates.withContact("본문")).isEqualTo("""
                본문

                문의 firstory.official@gmail.com
                인스타그램 @firstory.official""");
    }

    @Test
    void 주소_설정이_없으면_HTML을_만들지_않는다() {
        assertThat(new MailTemplates(null, "https://app.test").html(MailTemplates.STORY_STARTED, Map.of())).isNull();
        assertThat(new MailTemplates("https://api.test/mail", " ").html(MailTemplates.VERIFY_SIGNUP,
                Map.of("code", "1"))).isNull();
    }
}
