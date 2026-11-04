package com.firstory.domain.generation.entity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageTextBlockTest {

    private static PageTextBlock block(double x, double y, TextColor color, String... lines) {
        return new PageTextBlock(List.of(List.of(lines)), x, y, color);
    }

    private static PageTextBlock ok() {
        return block(5.0, 7.0, TextColor.DARK, "유치원 그림 시간이었어요.", "토끼 라라는 분홍 앞치마를 입었어요.");
    }

    @Test
    void 정상_덩어리_둘은_그대로_통과한다() {
        List<PageTextBlock> raw = List.of(ok(), block(55.0, 7.0, TextColor.LIGHT, "라라는 미나에게 꽃을 보여 주고 싶었어요."));

        assertThat(PageTextBlock.validated(raw)).isEqualTo(raw);
    }

    @Test
    void 덩어리_하나도_통과하고_경계값_0과_100도_통과한다() {
        assertThat(PageTextBlock.validated(List.of(ok()))).hasSize(1);
        assertThat(PageTextBlock.validated(List.of(block(0, 100, TextColor.DARK, "줄.")))).hasSize(1);
    }

    @Test
    void 문단이_여럿이어도_통과한다() {
        PageTextBlock twoParagraphs = new PageTextBlock(List.of(List.of("첫째."), List.of("둘째.", "셋째.")), 5, 7,
                TextColor.DARK);

        assertThat(PageTextBlock.validated(List.of(twoParagraphs))).containsExactly(twoParagraphs);
    }

    @Test
    void 덩어리가_0개나_3개_이상이면_null() {
        assertThat(PageTextBlock.validated(null)).isNull();
        assertThat(PageTextBlock.validated(List.of())).isNull();
        assertThat(PageTextBlock.validated(List.of(ok(), ok(), ok()))).isNull();
    }

    @Test
    void 좌표가_0에서_100_밖이면_null() {
        assertThat(PageTextBlock.validated(List.of(block(120, 7, TextColor.DARK, "줄.")))).isNull();
        assertThat(PageTextBlock.validated(List.of(block(-0.1, 7, TextColor.DARK, "줄.")))).isNull();
        assertThat(PageTextBlock.validated(List.of(block(5, 100.1, TextColor.DARK, "줄.")))).isNull();
        assertThat(PageTextBlock.validated(List.of(block(Double.NaN, 7, TextColor.DARK, "줄.")))).isNull();
    }

    @Test
    void 빈_줄_빈_문단_문단_없음이면_null() {
        assertThat(PageTextBlock.validated(List.of(block(5, 7, TextColor.DARK, "줄.", "  ")))).isNull();
        assertThat(PageTextBlock.validated(List.of(block(5, 7, TextColor.DARK, "")))).isNull();
        assertThat(PageTextBlock.validated(List.of(new PageTextBlock(List.of(), 5, 7, TextColor.DARK)))).isNull();
        assertThat(PageTextBlock.validated(List.of(new PageTextBlock(List.of(List.of()), 5, 7, TextColor.DARK))))
                .isNull();
        assertThat(PageTextBlock.validated(List.of(new PageTextBlock(null, 5, 7, TextColor.DARK)))).isNull();
        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        assertThat(PageTextBlock.validated(List.of(new PageTextBlock(List.of(withNull), 5, 7, TextColor.DARK))))
                .isNull();
    }

    @Test
    void 색이_없으면_null() {
        assertThat(PageTextBlock.validated(List.of(block(5, 7, null, "줄.")))).isNull();
    }

    @Test
    void 덩어리_하나라도_깨졌으면_쪽_전체가_null() {
        assertThat(PageTextBlock.validated(List.of(ok(), block(5, 700, TextColor.LIGHT, "줄.")))).isNull();
    }

    @Test
    void 덩어리_안에_null이_있으면_null() {
        List<PageTextBlock> withNull = new ArrayList<>();
        withNull.add(null);
        assertThat(PageTextBlock.validated(withNull)).isNull();
    }

    @Test
    void 색은_AI_값이_소문자이고_모르는_값은_null() {
        assertThat(TextColor.LIGHT.aiValue()).isEqualTo("light");
        assertThat(TextColor.fromAi("light")).isEqualTo(TextColor.LIGHT);
        assertThat(TextColor.fromAi("DARK")).isEqualTo(TextColor.DARK);
        assertThat(TextColor.fromAi("red")).isNull();
        assertThat(TextColor.fromAi(null)).isNull();
    }

    @Test
    void toString에_글이_나오지_않는다() {
        assertThat(ok().toString()).doesNotContain("유치원");
    }
}
