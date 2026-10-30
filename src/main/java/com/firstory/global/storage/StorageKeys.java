package com.firstory.global.storage;

import java.util.Locale;
import java.util.Map;

/**
 * 저장소 구현이 같이 쓰는 key 검사와 contentType.
 * <p>
 * key에 상위 폴더(..), 빈 칸(//), 절대 경로, 역슬래시, 드라이브 문자(:)가 있으면 거부한다.
 * AI 저장소 storage.py의 _check와 같은 기준이다. contentType은 따로 저장하지 않고 확장자로 정한다.
 */
final class StorageKeys {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "json", "application/json");
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private StorageKeys() {
    }

    static String validate(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("\\") || key.contains(":")
                || key.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("안전하지 않은 파일 key예요");
        }
        for (String segment : key.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("안전하지 않은 파일 key예요");
            }
        }
        return key;
    }

    static String contentTypeOf(String key) {
        int dot = key.lastIndexOf('.');
        if (dot < 0) {
            return DEFAULT_CONTENT_TYPE;
        }
        return CONTENT_TYPES.getOrDefault(key.substring(dot + 1).toLowerCase(Locale.ROOT), DEFAULT_CONTENT_TYPE);
    }
}
