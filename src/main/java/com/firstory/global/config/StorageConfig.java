package com.firstory.global.config;

import com.firstory.global.storage.FileStorage;
import com.firstory.global.storage.LocalFileStorage;
import com.firstory.global.storage.S3FileStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;

/**
 * 결과물 파일 저장소. firstory.storage.type으로 고른다. local(기본)은 로컬 폴더, s3는 S3 버킷.
 * 진짜 AI 서버와 다른 컴퓨터에서 돌면 둘이 같은 파일을 봐야 하므로 s3를 쓴다.
 */
@Configuration
public class StorageConfig {

    @Bean
    public FileStorage fileStorage(FirstoryProperties properties) {
        FirstoryProperties.Storage storage = properties.storage();
        return switch (storage.type()) {
            case LOCAL -> new LocalFileStorage(Path.of(storage.localRoot()));
            case S3 -> s3FileStorage(storage.s3());
        };
    }

    private static FileStorage s3FileStorage(FirstoryProperties.Storage.S3 s3) {
        if (s3 == null || s3.bucket() == null || s3.bucket().isBlank()
                || s3.region() == null || s3.region().isBlank()) {
            throw new IllegalStateException("firstory.storage.type=s3면 STORAGE_S3_BUCKET과 STORAGE_S3_REGION이 필요해요");
        }
        S3Client client = S3Client.builder().region(Region.of(s3.region())).build();
        return new S3FileStorage(client, s3.bucket());
    }
}
