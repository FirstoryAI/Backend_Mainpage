package com.firstory.domain.story.dto;

/**
 * 아이에게 전하고 싶은 말 (CRT-04).
 *
 * @param message null이거나 비어 있으면 "아직 잘 모르겠어요"
 */
// TODO(PM): 전하고 싶은 말 글자 수 제한 미정. 지금은 제한하지 않는다
public record MessageRequest(String message) {

    // 부모 입력이 로그에 남지 않게 한다
    @Override
    public String toString() {
        return "MessageRequest[***]";
    }
}
