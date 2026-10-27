package com.firstory.global.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 로컬 폴더에 파일을 둔다 (firstory.storage.local-root).
 * <p>
 * 경로 보안: StorageKeys.validate로 key를 검사하고,
 * 루트 아래로 바꿔 normalize()한 결과가 루트 밖이면 한 번 더 거부한다.
 */
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Optional<StoredFile> read(String key) {
        Path path = resolve(key);
        try {
            return Optional.of(new StoredFile(Files.readAllBytes(path), StorageKeys.contentTypeOf(key)));
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
        StorageKeys.validate(key);
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("안전하지 않은 파일 key예요");
        }
        return path;
    }
}
