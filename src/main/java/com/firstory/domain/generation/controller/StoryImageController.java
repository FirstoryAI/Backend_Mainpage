package com.firstory.domain.generation.controller;

import com.firstory.domain.generation.service.StoryImageService;
import com.firstory.global.error.ApiErrorCodes;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.security.UserPrincipal;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 그림 내려주기. 주소에는 DB id만 들어가고 파일 위치는 DB에 저장된 key로만 찾는다.
 * 오류는 JSON으로 내려야 해서 produces를 걸지 않고 응답에서 Content-Type을 정한다.
 */
@Tag(name = "동화 그림", description = "로그인 필요. 내 동화의 버전과 쪽이 아니면 모두 404")
@ApiErrorCodes({ErrorCode.UNAUTHORIZED, ErrorCode.STORY_NOT_FOUND, ErrorCode.VERSION_NOT_FOUND,
        ErrorCode.IMAGE_NOT_FOUND})
@RestController
@RequestMapping("/api/stories/{id:\\d+}/versions/{versionId:\\d+}")
@RequiredArgsConstructor
public class StoryImageController {

    private static final Duration CACHE_FOR = Duration.ofHours(1);

    private final StoryImageService storyImageService;

    @Operation(summary = "쪽 그림", description = "그림이 실패한 쪽(FAILED)이나 파일이 없으면 IMAGE_NOT_FOUND")
    @ApiResponse(responseCode = "200", description = "PNG 그림",
            content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/pages/{pageNo:\\d+}/image")
    public ResponseEntity<byte[]> pageImage(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                            @PathVariable Long versionId, @PathVariable int pageNo) {
        return image(storyImageService.pageImage(principal.getUserId(), id, versionId, pageNo));
    }

    @Operation(summary = "캐릭터 시트", description = "캐릭터 시트가 없으면 IMAGE_NOT_FOUND")
    @ApiResponse(responseCode = "200", description = "PNG 그림",
            content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/character-sheet")
    public ResponseEntity<byte[]> characterSheet(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable Long id, @PathVariable Long versionId) {
        return image(storyImageService.characterSheet(principal.getUserId(), id, versionId));
    }

    private static ResponseEntity<byte[]> image(StoredFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(CACHE_FOR).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .body(file.bytes());
    }
}
