package com.firstory.global.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path temp;

    @ParameterizedTest
    @ValueSource(strings = {"../etc/passwd", "stories/1/../../../etc/passwd", "/etc/passwd", "C:/Windows/win.ini",
            "stories\\1\\page.png", "stories//1/page.png", "./stories/1/page.png", "stories/1/", ""})
    void 안전하지_않은_key는_거부한다(String key) {
        LocalFileStorage storage = new LocalFileStorage(temp.resolve("root"));

        assertThatThrownBy(() -> storage.read(key)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.write(key, new byte[]{1}, "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.exists(key)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 절대_경로_key는_루트_밖을_가리켜도_거부한다() {
        LocalFileStorage storage = new LocalFileStorage(temp.resolve("root"));

        assertThatThrownBy(() -> storage.read(temp.resolve("outside.png").toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 루트_안에_쓰고_읽는다() {
        Path root = temp.resolve("root");
        LocalFileStorage storage = new LocalFileStorage(root);
        String key = "stories/12/runs/1/images/page-01.png";

        storage.write(key, new byte[]{1, 2, 3}, "image/png");

        assertThat(storage.exists(key)).isTrue();
        StoredFile file = storage.read(key).orElseThrow();
        assertThat(file.bytes()).containsExactly(1, 2, 3);
        assertThat(file.contentType()).isEqualTo("image/png");
        assertThat(Files.exists(root.resolve(key))).isTrue();
    }

    @Test
    void 한글_이름도_쓸_수_있다() {
        LocalFileStorage storage = new LocalFileStorage(temp.resolve("root"));
        String key = "stories/12/runs/1/images/sheet-main-아로.png";

        storage.write(key, new byte[]{9}, "image/png");

        assertThat(storage.read(key)).isPresent();
    }

    @Test
    void 없는_파일은_empty() {
        LocalFileStorage storage = new LocalFileStorage(temp.resolve("root"));

        assertThat(storage.read("stories/1/runs/1/images/page-01.png")).isEmpty();
        assertThat(storage.exists("stories/1/runs/1/images/page-01.png")).isFalse();
    }

    @Test
    void 같은_key에_다시_쓰면_바꾼다() {
        LocalFileStorage storage = new LocalFileStorage(temp.resolve("root"));
        String key = "stories/1/runs/1/images/page-01.png";

        storage.write(key, new byte[]{1}, "image/png");
        storage.write(key, new byte[]{2}, "image/png");

        assertThat(storage.read(key).orElseThrow().bytes()).containsExactly(2);
    }
}
