package com.firstory.global.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

/**
 * S3 버킷에 파일을 둔다 (firstory.storage.type=s3). key가 그대로 객체 key다.
 * <p>
 * 진짜 AI 서버도 같은 버킷에 같은 key로 쓴다. 서버가 여러 대여도 같은 파일을 본다.
 * 접근 키는 설정에 두지 않고 AWS 기본 방식으로 찾는다 (환경 변수 AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY 또는 EC2 IAM 역할).
 */
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final String bucket;

    public S3FileStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public Optional<StoredFile> read(String key) {
        StorageKeys.validate(key);
        try {
            byte[] bytes = s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
            return Optional.of(new StoredFile(bytes, StorageKeys.contentTypeOf(key)));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void write(String key, byte[] bytes, String contentType) {
        StorageKeys.validate(key);
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(bytes));
    }

    @Override
    public boolean exists(String key) {
        StorageKeys.validate(key);
        try {
            s3.headObject(b -> b.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            // HEAD는 본문이 없어 없는 객체도 NoSuchKeyException이 아니라 상태 코드 404로만 온다
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
}
