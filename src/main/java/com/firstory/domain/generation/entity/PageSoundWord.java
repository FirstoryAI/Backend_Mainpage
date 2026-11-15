package com.firstory.domain.generation.entity;

/**
 * 그림 위 소리 주인 옆에 띄우는 의성어·의태어 하나. 쪽마다 0~1개다(본문 {@code **말**}이 있는 쪽).
 * 말이 부모 입력에서 나올 수 있어 toString에 넣지 않는다.
 *
 * @param text  띄울 말(별표 없이). 본문의 {@code **말**}과 같다
 * @param x     말 가운데. 그림 전체 폭에 대한 % (0~100)
 * @param y     말 가운데. 그림 전체 높이에 대한 % (0~100)
 * @param color 글자색 (DARK | LIGHT)
 */
public record PageSoundWord(String text, double x, double y, TextColor color) {

    /**
     * AI가 준 값을 검사한다: 말이 비지 않음, x·y 0~100, 색 있음. 어긋나면 그 쪽은 의성어 없이 보이게 null.
     */
    public static PageSoundWord validated(PageSoundWord raw) {
        if (raw == null || raw.text() == null || raw.text().isBlank() || raw.color() == null
                || !inRange(raw.x()) || !inRange(raw.y())) {
            return null;
        }
        return raw;
    }

    private static boolean inRange(double value) {
        return value >= 0 && value <= 100;   // NaN은 둘 다 거짓
    }

    @Override
    public String toString() {
        return "PageSoundWord[x=" + x + ", y=" + y + ", color=" + color + "]";
    }
}
