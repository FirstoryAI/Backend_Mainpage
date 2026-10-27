package com.firstory.global.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class S3FileStorageTest {

    private static final String KEY = "stories/1/runs/1/images/page-01.png";

    private final S3Client s3 = mock(S3Client.class);
    private final S3FileStorage storage = new S3FileStorage(s3, "bucket");

    @ParameterizedTest
    @ValueSource(strings = {"../etc/passwd", "/etc/passwd", "C:/Windows/win.ini", "stories\\1\\page.png",
            "stories//1/page.png", "./stories/1/page.png", "stories/1/", ""})
    void 안전하지_않은_key는_S3를_부르지_않고_거부한다(String key) {
        assertThatThrownBy(() -> storage.read(key)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.write(key, new byte[]{1}, "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.exists(key)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(s3);
    }

    @Test
    void 버킷의_key를_읽고_contentType은_확장자로_정한다() {
        when(s3.getObjectAsBytes(any(Consumer.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[]{1, 2}));

        StoredFile file = storage.read(KEY).orElseThrow();

        assertThat(file.bytes()).containsExactly(1, 2);
        assertThat(file.contentType()).isEqualTo("image/png");
        ArgumentCaptor<Consumer<GetObjectRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(s3).getObjectAsBytes(captor.capture());
        GetObjectRequest.Builder builder = GetObjectRequest.builder();
        captor.getValue().accept(builder);
        assertThat(builder.build()).isEqualTo(GetObjectRequest.builder().bucket("bucket").key(KEY).build());
    }

    @Test
    void 없는_파일은_empty() {
        when(s3.getObjectAsBytes(any(Consumer.class))).thenThrow(NoSuchKeyException.builder().build());

        assertThat(storage.read(KEY)).isEmpty();
    }

    @Test
    void 버킷의_key에_contentType과_함께_쓴다() {
        storage.write(KEY, new byte[]{1}, "image/png");

        ArgumentCaptor<Consumer<PutObjectRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest.Builder builder = PutObjectRequest.builder();
        captor.getValue().accept(builder);
        assertThat(builder.build()).isEqualTo(
                PutObjectRequest.builder().bucket("bucket").key(KEY).contentType("image/png").build());
    }

    @Test
    void exists는_HEAD로_확인하고_404면_false() {
        when(s3.headObject(any(Consumer.class))).thenReturn(HeadObjectResponse.builder().build());
        assertThat(storage.exists(KEY)).isTrue();

        when(s3.headObject(any(Consumer.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThat(storage.exists(KEY)).isFalse();
    }

    @Test
    void exists는_404가_아닌_오류를_그대로_던진다() {
        when(s3.headObject(any(Consumer.class))).thenThrow(S3Exception.builder().statusCode(403).build());

        assertThatThrownBy(() -> storage.exists(KEY)).isInstanceOf(S3Exception.class);
    }
}
