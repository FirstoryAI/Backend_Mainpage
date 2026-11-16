package com.firstory.global.entity;

/**
 * 피드백 팝업(확정 전, 함께 읽기 끝)의 선택 답. DB에는 코드(name)로 저장하고 화면 문구(label)는 enum에만 둔다.
 * <p>
 * 요청은 코드와 화면 문구를 모두 받는다. 프론트가 처음에 문구를 그대로 보내도록 만들어져 있어서다 (Backend#76).
 */
public interface FeedbackChoice {

    String getLabel();

    /**
     * 각 enum의 {@code @JsonCreator}가 부른다.
     *
     * @throws IllegalArgumentException 코드도 문구도 아니면. 요청 오류는 "고를 수 없는 값이에요."가 된다
     */
    static <E extends Enum<E> & FeedbackChoice> E of(Class<E> type, String value) {
        for (E choice : type.getEnumConstants()) {
            if (choice.name().equals(value) || choice.getLabel().equals(value)) {
                return choice;
            }
        }
        throw new IllegalArgumentException("고를 수 없는 값이에요.");
    }
}
