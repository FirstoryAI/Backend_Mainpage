package com.firstory.global.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 로컬 폴더에 파일을 둔다 (firstory.storage.local-root).
 * <p>
 * 경로 보안: key에 상위 폴더(..), 절대 경로, 역슬래시, 드라이브 문자(:)가 있으면 거부한다.
 * 그다음 루트 아래로 바꿔 normalize()한 결과가 루트 밖이면 한 번 더 거부한다.
 * contentType은 따로 저장하지 않고 확장자로 정한다.
 */
public class LocalFileStorage implements FileStorage {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "json", "application/json");
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final Path root;

    public LocalFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Optional<StoredFile> read(String key) {
        Path path = resolve(key);
        try {
            return Optional.of(new StoredFile(Files.readAllBytes(path), contentTypeOf(key)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 읽지 못했어요", e);
        }
    }

    @Override
    public void write(String key, byte[] bytes, String contentType) {
        Path path = resolve(key);
        try {
            Files.createDirectories(path.getParent());
            // 같은 key를 읽는 쪽이 반쯤 쓴 파일을 보지 않게 임시 파일에 쓰고 옮긴다
            Path temp = Files.createTempFile(path.getParent(), ".upload-", ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 쓰지 못했어요", e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    Path resolve(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("\\") || key.contains(":")
                || key.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("안전하지 않은 파일 key예요");
        }
        for (String segment : key.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("안전하지 않은 파일 key예요");
            }
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("안전하지 않은 파일 key예요");
        }
        return path;
    }

    private static String contentTypeOf(String key) {
        int dot = key.lastIndexOf('.');
        if (dot < 0) {
            return DEFAULT_CONTENT_TYPE;
        }
        return CONTENT_TYPES.getOrDefault(key.substring(dot + 1).toLowerCase(Locale.ROOT), DEFAULT_CONTENT_TYPE);
    }
}
