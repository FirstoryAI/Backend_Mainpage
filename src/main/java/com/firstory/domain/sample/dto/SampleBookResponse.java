package com.firstory.domain.sample.dto;

import com.firstory.domain.generation.entity.ImageStatus;
import com.firstory.domain.generation.entity.StoryPage;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.reading.dto.BookResponse;

import java.util.List;

/**
 * 샘플 동화 읽기. 함께 읽기(BookResponse)와 같은 쪽 모양이고 대화 질문(prompts)은 없다.
 * 동화·버전 id는 내려주지 않는다. 그림은 카드 id로 된 샘플 그림 주소로만 받는다.
 *
 * @param coverUrl 표지와 캐릭터 시트가 모두 없으면 null
 */
public record SampleBookResponse(String cardId, String title, String coverUrl, int pageCount,
                                 List<BookResponse.Page> pages) {

    public static SampleBookResponse of(String cardId, String title, StoryVersion version, int pageCount,
                                        List<StoryPage> pages) {
        boolean hasCover = version.getCoverKey() != null || version.getCharacterSheetKey() != null;
        return new SampleBookResponse(cardId, title, hasCover ? coverUrl(cardId) : null, pageCount,
                pages.stream().map(page -> new BookResponse.Page(page.getPageNo(), page.getText(),
                        page.getImageStatus() == ImageStatus.DONE ? pageUrl(cardId, page.getPageNo()) : null,
                        page.getImageStatus(), page.getTextPosition(), page.getTextBlocks())).toList());
    }

    public static String coverUrl(String cardId) {
        return "/api/samples/" + cardId + "/cover";
    }

    public static String pageUrl(String cardId, int pageNo) {
        return "/api/samples/" + cardId + "/pages/" + pageNo + "/image";
    }
}
