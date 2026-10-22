package com.firstory.global.storage;

import java.util.Optional;

/**
 * 그림 같은 결과물 파일 저장소. key는 `stories/{storyId}/runs/{runNo}/images/page-01.png` 형식이다.
 * 지금은 로컬 폴더(LocalFileStorage)만 있다. S3로 바꿔도 이 인터페이스와 그림 API는 그대로 둔다.
 * <p>
 * key는 DB에 저장된 값으로만 만든다. 요청 값으로 key를 만들지 않는다.
 */
public interface FileStorage {

    /**
     * @return 파일이 없으면 empty
     * @throws IllegalArgumentException key가 안전하지 않으면 (상위 폴더, 절대 경로, 역슬래시)
     */
    Optional<StoredFile> read(String key);

    /**
     * 가짜 AI와 테스트가 쓴다. 진짜 AI는 자기 쪽에서 파일을 쓴다.
     */
    void write(String key, byte[] bytes, String contentType);

    boolean exists(String key);
}
