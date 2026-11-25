package com.firstory.domain.generation.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 그림 위 소리 근원(얼굴·물건) 옆에 띄우는 의성어·의태어 하나. 쪽마다 0~1개다(본문 {@code **말**}이 있는 쪽).
 * 같은 두 글자가 반복되는 4글자 말은 두 조각(근원 양옆), 나머지는 한 조각이다. 색은 AI가 그 자리 배경과 구별되게 고른다.
 * 말이 부모 입력에서 나올 수 있어 toString에 넣지 않는다.
 * 옛 모양({text, x, y, color: DARK|LIGHT})은 모르는 칸을 무시하고 읽혀 pieces가 null이 되고, {@link #validated}에서 null이 된다.
 *
 * @param text   띄울 말(별표 없이). 본문의 {@code **말**}과 같다
 * @param color  글자색 {@code #RRGGBB}
 * @param pieces 조각 1~2개. 이으면 text(공백 뺀 것)와 같다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageSoundWord(String text, String color, List<Piece> pieces) {

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    /**
     * 조각 하나.
     *
     * @param text 조각 글
     * @param x    조각 가운데. 그림 전체 폭에 대한 % (0~100)
     * @param y    조각 가운데. 그림 전체 높이에 대한 % (0~100)
     * @param side 근원의 어느 쪽인지
     */
    public record Piece(String text, double x, double y, PieceSide side) {

        @Override
        public String toString() {
            return "Piece[x=" + x + ", y=" + y + ", side=" + side + "]";
        }
    }

    /**
     * 검사한다: 말이 비지 않음, 색이 #RRGGBB, 조각 1~2개, 조각 글이 비지 않고 x·y 0~100, 방향 있음, 조각을 이으면 말(공백 뺀 것).
     * 어긋나면 그 쪽은 의성어 없이 보이게 null.
     */
    public static PageSoundWord validated(PageSoundWord raw) {
        if (raw == null || raw.text() == null || raw.text().isBlank() || raw.color() == null
                || !HEX.matcher(raw.color()).matches() || raw.pieces() == null
                || raw.pieces().isEmpty() || raw.pieces().size() > 2) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        for (Piece piece : raw.pieces()) {
            if (piece == null || piece.text() == null || piece.text().isBlank() || piece.side() == null
                    || !inRange(piece.x()) || !inRange(piece.y())) {
                return null;
            }
            joined.append(piece.text());
        }
        return joined.toString().equals(raw.text().replace(" ", "")) ? raw : null;
    }

    /**
     * 부모가 미리보기에서 옮긴 조각 자리. 받은 조각(순번)의 x·y만 바꾸고 글·색·방향과 받지 않은 조각은 그대로 둔다.
     *
     * @param moved 순번 → [x, y] (%). 순번이 조각 범위 밖이거나 좌표가 0~100 밖이면 IllegalArgumentException
     */
    public PageSoundWord withPositions(Map<Integer, double[]> moved) {
        List<Piece> next = new ArrayList<>(pieces);
        moved.forEach((index, xy) -> {
            if (index == null || index < 0 || index >= next.size() || !inRange(xy[0]) || !inRange(xy[1])) {
                throw new IllegalArgumentException("조각 순번이나 좌표가 범위 밖이에요");
            }
            Piece old = next.get(index);
            next.set(index, new Piece(old.text(), xy[0], xy[1], old.side()));
        });
        return new PageSoundWord(text, color, List.copyOf(next));
    }

    private static boolean inRange(double value) {
        return value >= 0 && value <= 100;   // NaN은 둘 다 거짓
    }

    @Override
    public String toString() {
        return "PageSoundWord[color=" + color + ", pieces=" + pieces + "]";
    }
}
