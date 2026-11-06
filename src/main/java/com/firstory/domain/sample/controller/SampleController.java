package com.firstory.domain.sample.controller;

import com.firstory.domain.sample.dto.SampleBookResponse;
import com.firstory.domain.sample.service.SampleService;
import com.firstory.global.error.ApiErrorCodes;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.storage.StoredFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 홈 샘플 카드의 미리 만들어 둔 동화. 로그인 없이 읽는다. 카드와 동화는 SAMPLE_STORIES 설정으로 연결한다.
 * 누구에게나 같은 그림이라 공용 캐시로 내린다. 카드에 다른 동화를 연결하면 길어도 CACHE_FOR 뒤에 바뀐다.
 */
@Tag(name = "샘플 동화", description = "로그인 불필요. 홈 샘플 카드 id로 미리 만들어 둔 동화를 읽는다. 대화 가이드는 없다")
@ApiErrorCodes(ErrorCode.SAMPLE_NOT_FOUND)
@RestController
@RequestMapping("/api/samples/{cardId:[A-Za-z][A-Za-z0-9_-]{0,49}}")
@RequiredArgsConstructor
public class SampleController {

    private static final Duration CACHE_FOR = Duration.ofHours(1);
    private static final String IMAGE_WEBP = "image/webp";

    private final SampleService sampleService;

    @Operation(summary = "샘플 동화 읽기", description = "표지와 쪽별 글·그림. 함께 읽기(/book)와 같은 쪽 모양이고 prompts는 없다")
    @GetMapping
    public SampleBookResponse book(@PathVariable String cardId) {
        return sampleService.book(cardId);
    }

    @Operation(summary = "샘플 동화 표지", description = "표지가 없으면 캐릭터 시트. 둘 다 없으면 IMAGE_NOT_FOUND")
    @ApiErrorCodes(ErrorCode.IMAGE_NOT_FOUND)
    @ApiResponse(responseCode = "200", description = "WebP 또는 PNG 그림",
            content = {@Content(mediaType = IMAGE_WEBP, schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary"))})
    @GetMapping("/cover")
    public ResponseEntity<byte[]> cover(@PathVariable String cardId) {
        return image(sampleService.cover(cardId));
    }

    @Operation(summary = "샘플 동화 쪽 그림", description = "그림이 실패한 쪽이나 없는 쪽이면 IMAGE_NOT_FOUND")
    @ApiErrorCodes(ErrorCode.IMAGE_NOT_FOUND)
    @ApiResponse(responseCode = "200", description = "WebP 또는 PNG 그림",
            content = {@Content(mediaType = IMAGE_WEBP, schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary"))})
    @GetMapping("/pages/{pageNo:\\d+}/image")
    public ResponseEntity<byte[]> pageImage(@PathVariable String cardId, @PathVariable int pageNo) {
        return image(sampleService.pageImage(cardId, pageNo));
    }

    private static ResponseEntity<byte[]> image(StoredFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(CACHE_FOR).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .body(file.bytes());
    }
}
