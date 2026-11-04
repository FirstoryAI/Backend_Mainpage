package com.firstory.domain.generation.entity;

import java.util.List;

/**
 * 그림 위에 얹을 글 덩어리 하나. 쪽마다 1~2개다.
 * 줄바꿈이 끝난 상태라 프론트는 받은 줄 그대로 그린다. 글에 부모 입력이 반영되어 있어 toString에 내용을 넣지 않는다.
 *
 * @param paragraphs 문단 > 줄. 줄 글의 {@code **강조**}는 그대로 둔다(프론트가 굵게 그린다)
 * @param x          덩어리 왼쪽 위 모서리. 그림 전체 폭에 대한 % (0~100)
 * @param y          덩어리 왼쪽 위 모서리. 그림 전체 높이에 대한 % (0~100)
 * @param color      글자색 (DARK | LIGHT)
 */
public record PageTextBlock(List<List<String>> paragraphs, double x, double y, TextColor color) {

    public static final int MAX_BLOCKS_PER_PAGE = 2;

    /**
     * AI가 준 덩어리를 검사한다: 1~2개, x·y 0~100, 문단·줄이 비지 않음, 색 있음.
     * 하나라도 어긋나면 그 쪽은 덩어리 없이 옛 방식(textPosition)으로 보이게 null을 돌려준다.
     */
    public static List<PageTextBlock> validated(List<PageTextBlock> raw) {
        if (raw == null || raw.isEmpty() || raw.size() > MAX_BLOCKS_PER_PAGE) {
            return null;
        }
        for (PageTextBlock block : raw) {
            if (block == null || !block.isValid()) {
                return null;
            }
        }
        return List.copyOf(raw);
    }

    private boolean isValid() {
        if (color == null || !inRange(x) || !inRange(y) || paragraphs == null || paragraphs.isEmpty()) {
            return false;
        }
        return paragraphs.stream().allMatch(paragraph -> paragraph != null && !paragraph.isEmpty()
                && paragraph.stream().allMatch(line -> line != null && !line.isBlank()));
    }

    private static boolean inRange(double value) {
        return value >= 0 && value <= 100;   // NaN은 둘 다 거짓
    }

    @Override
    public String toString() {
        return "PageTextBlock[x=" + x + ", y=" + y + ", color=" + color + "]";
    }
}
