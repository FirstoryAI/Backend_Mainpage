package com.firstory.global.config;

import com.firstory.global.storage.FileStorage;
import com.firstory.global.storage.LocalFileStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * 결과물 파일 저장소. 지금은 로컬 폴더만. S3는 같은 FileStorage 인터페이스로 나중에 추가한다.
 */
@Configuration
public class StorageConfig {

    @Bean
    public FileStorage fileStorage(FirstoryProperties properties) {
        return new LocalFileStorage(Path.of(properties.storage().localRoot()));
    }
}
