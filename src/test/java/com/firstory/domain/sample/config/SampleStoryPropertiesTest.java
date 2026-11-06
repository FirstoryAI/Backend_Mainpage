package com.firstory.domain.sample.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SampleStoryPropertiesTest {

    @Test
    void 카드id와_동화id를_쉼표로_적은_목록을_읽는다() {
        var properties = new SampleStoryProperties(List.of(" friend:41", "emotion:42 ", ""));

        assertThat(properties.storyIds()).isEqualTo(Map.of("friend", 41L, "emotion", 42L));
    }

    @Test
    void 비어_있으면_연결이_없다() {
        assertThat(new SampleStoryProperties(null).storyIds()).isEmpty();
        assertThat(new SampleStoryProperties(List.of()).storyIds()).isEmpty();
    }

    @Test
    void 형식이_틀리면_기동하지_않는다() {
        for (String entry : List.of("friend", "friend:", ":41", "friend:abc", "친구:41", "friend:41:1", "friend 41")) {
            assertThatThrownBy(() -> new SampleStoryProperties(List.of(entry)))
                    .as(entry).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void 같은_카드가_두_번_있으면_기동하지_않는다() {
        assertThatThrownBy(() -> new SampleStoryProperties(List.of("friend:41", "friend:42")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("friend");
    }
}
