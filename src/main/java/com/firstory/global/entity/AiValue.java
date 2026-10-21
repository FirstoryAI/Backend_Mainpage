package com.firstory.global.entity;

import java.util.Locale;

/**
 * AI 서비스와 값을 주고받는 enum. 값 이름은 AI와 같은 단어를 쓴다.
 * DB와 Java는 대문자(name), AI로 보낼 때는 소문자(aiValue)로 바꾼다. 따로 변환표를 두지 않는다.
 */
public interface AiValue {

    String name();

    default String aiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
