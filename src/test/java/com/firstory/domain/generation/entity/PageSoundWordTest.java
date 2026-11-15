package com.firstory.domain.generation.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageSoundWordTest {

    @Test
    void 정상_값은_그대로_통과한다() {
        PageSoundWord word = new PageSoundWord("훌쩍훌쩍", 62.5, 31.0, TextColor.DARK);

        assertThat(PageSoundWord.validated(word)).isEqualTo(word);
    }

    @Test
    void 경계값_0과_100도_통과한다() {
        assertThat(PageSoundWord.validated(new PageSoundWord("톡", 0, 100, TextColor.LIGHT))).isNotNull();
    }

    @Test
    void null이면_null() {
        assertThat(PageSoundWord.validated(null)).isNull();
    }

    @Test
    void 빈_말이나_공백이면_null() {
        assertThat(PageSoundWord.validated(new PageSoundWord("", 50, 50, TextColor.DARK))).isNull();
        assertThat(PageSoundWord.validated(new PageSoundWord("  ", 50, 50, TextColor.DARK))).isNull();
        assertThat(PageSoundWord.validated(new PageSoundWord(null, 50, 50, TextColor.DARK))).isNull();
    }

    @Test
    void 좌표가_0에서_100_밖이거나_NaN이면_null() {
        assertThat(PageSoundWord.validated(new PageSoundWord("톡", -0.1, 50, TextColor.DARK))).isNull();
        assertThat(PageSoundWord.validated(new PageSoundWord("톡", 50, 100.1, TextColor.DARK))).isNull();
        assertThat(PageSoundWord.validated(new PageSoundWord("톡", Double.NaN, 50, TextColor.DARK))).isNull();
    }

    @Test
    void 색이_없으면_null() {
        assertThat(PageSoundWord.validated(new PageSoundWord("톡", 50, 50, null))).isNull();
    }

    @Test
    void toString에_말이_없다() {
        assertThat(new PageSoundWord("비밀말", 50, 50, TextColor.DARK).toString()).doesNotContain("비밀말");
    }
}
