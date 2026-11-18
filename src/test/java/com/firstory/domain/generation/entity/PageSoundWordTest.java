package com.firstory.domain.generation.entity;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageSoundWordTest {

    private static PageSoundWord.Piece piece(String text, double x, double y, PieceSide side) {
        return new PageSoundWord.Piece(text, x, y, side);
    }

    private static final PageSoundWord TWO = new PageSoundWord("데굴데굴", "#E03E4E",
            List.of(piece("데굴", 18.2, 46.0, PieceSide.LEFT), piece("데굴", 37.9, 46.0, PieceSide.RIGHT)));

    @Test
    void 두_조각_정상_값은_그대로_통과한다() {
        assertThat(PageSoundWord.validated(TWO)).isEqualTo(TWO);
    }

    @Test
    void 한_조각도_통과한다() {
        PageSoundWord one = new PageSoundWord("엉엉", "#7B4FD6", List.of(piece("엉엉", 50, 30, PieceSide.CENTER)));
        assertThat(PageSoundWord.validated(one)).isEqualTo(one);
    }

    @Test
    void 공백이_든_말도_조각을_이으면_통과한다() {
        PageSoundWord spaced = new PageSoundWord("데굴 데굴", "#7B4FD6",
                List.of(piece("데굴", 10, 30, PieceSide.LEFT), piece("데굴", 30, 30, PieceSide.RIGHT)));
        assertThat(PageSoundWord.validated(spaced)).isEqualTo(spaced);
    }

    @Test
    void 경계값_0과_100도_통과한다() {
        assertThat(PageSoundWord.validated(new PageSoundWord("톡", "#FFFFFF", List.of(piece("톡", 0, 100, PieceSide.CENTER)))))
                .isNotNull();
    }

    @Test
    void null이면_null() {
        assertThat(PageSoundWord.validated(null)).isNull();
    }

    @Test
    void 빈_말이면_null() {
        assertThat(PageSoundWord.validated(new PageSoundWord(" ", "#FFFFFF", List.of(piece(" ", 1, 1, PieceSide.CENTER))))).isNull();
        assertThat(PageSoundWord.validated(new PageSoundWord(null, "#FFFFFF", List.of(piece("톡", 1, 1, PieceSide.CENTER))))).isNull();
    }

    @Test
    void 색이_색_코드가_아니면_null() {
        for (String color : new String[]{"red", "#FFF", "7B4FD6", "#GGGGGG", null}) {
            assertThat(PageSoundWord.validated(new PageSoundWord("톡", color, List.of(piece("톡", 1, 1, PieceSide.CENTER)))))
                    .as(String.valueOf(color)).isNull();
        }
    }

    @Test
    void 조각이_없거나_세_개_이상이면_null() {
        assertThat(PageSoundWord.validated(new PageSoundWord("톡", "#FFFFFF", null))).isNull();
        assertThat(PageSoundWord.validated(new PageSoundWord("톡", "#FFFFFF", List.of()))).isNull();
        assertThat(PageSoundWord.validated(new PageSoundWord("톡톡톡", "#FFFFFF", List.of(
                piece("톡", 1, 1, PieceSide.LEFT), piece("톡", 2, 1, PieceSide.CENTER), piece("톡", 3, 1, PieceSide.RIGHT))))).isNull();
    }

    @Test
    void 조각을_이은_글이_말과_다르면_null() {
        assertThat(PageSoundWord.validated(new PageSoundWord("데굴데굴", "#FFFFFF",
                List.of(piece("데굴", 1, 1, PieceSide.LEFT), piece("구르", 2, 1, PieceSide.RIGHT))))).isNull();
    }

    @Test
    void 조각_좌표가_0에서_100_밖이거나_NaN이거나_방향이_없으면_null() {
        assertThat(PageSoundWord.validated(new PageSoundWord("톡", "#FFFFFF", List.of(piece("톡", 101, 1, PieceSide.CENTER))))).isNull();
        assertThat(PageSoundWord.validated(new PageSoundWord("톡", "#FFFFFF", List.of(piece("톡", Double.NaN, 1, PieceSide.CENTER))))).isNull();
        assertThat(PageSoundWord.validated(new PageSoundWord("톡", "#FFFFFF", List.of(piece("톡", 1, 1, null))))).isNull();
        assertThat(PageSoundWord.validated(new PageSoundWord("톡", "#FFFFFF", Arrays.asList((PageSoundWord.Piece) null)))).isNull();
    }

    @Test
    void toString에_말이_없다() {
        assertThat(TWO.toString()).doesNotContain("데굴");
    }
}
