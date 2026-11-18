package com.firstory.domain.reading.dto;

import com.firstory.domain.generation.dto.PreviewResponse;
import com.firstory.domain.generation.dto.StoryImageUrls;
import com.firstory.domain.generation.entity.ImageStatus;
import com.firstory.domain.generation.entity.PageSoundWord;
import com.firstory.domain.generation.entity.PageTextBlock;
import com.firstory.domain.generation.entity.StoryPage;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.generation.entity.TextPosition;
import com.firstory.domain.guide.entity.GuidePrompt;
import com.firstory.domain.story.dto.StoryCardResponse;
import com.firstory.domain.story.entity.Story;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 함께 읽기 화면 (RD-01). 확정된 버전의 표지와 쪽, 쪽 사이에 나올 대화 질문.
 * 그림은 imageUrl(그림 API)로 받는다. 파일 key나 저장 경로는 넣지 않는다.
 *
 * @param coverUrl      캐릭터 시트가 없으면 null
 * @param frontCoverUrl 책 맨 앞 앞표지(정사각형 표지와 같은 그림, 옛 버전은 가로). 없으면(이전 버전, 표지 실패) null
 * @param backCoverUrl  책 맨 뒤 뒤표지(정사각형, 글자 없음, 옛 버전은 가로). 없으면 null
 * @param prompts       afterPage 쪽을 읽은 뒤에 보여준다 (RD-02)
 */
public record BookResponse(Long storyId, Long versionId, String title, String coverUrl, String frontCoverUrl,
                           String backCoverUrl, int pageCount, List<Page> pages, List<Prompt> prompts) {

    /**
     * @param imageUrl     그림이 실패한 쪽(FAILED)은 null
     * @param textPosition 옛 화면용 글 자리. textBlocks가 null일 때만 이걸로 그린다
     * @param textBlocks   글 덩어리 배치(1~2개). 옛 동화와 그림이 실패한 쪽은 null
     * @param soundWord    의성어·의태어 조각(1~2개, 조각 가운데 x·y %)과 색 #RRGGBB. 옛 동화·별표 말 없는 쪽·그림 실패 쪽은 null
     */
    public record Page(int pageNo, String text, String imageUrl, ImageStatus imageStatus,
                       TextPosition textPosition,
                       @Schema(example = PreviewResponse.TEXT_BLOCKS_EXAMPLE) List<PageTextBlock> textBlocks,
                       @Schema(example = PreviewResponse.SOUND_WORD_EXAMPLE) PageSoundWord soundWord) {
    }

    public record Prompt(Long id, int afterPage, String question, String parentHint) {
    }

    public static BookResponse of(Story story, StoryVersion version, int pageCount, List<StoryPage> pages,
                                  List<GuidePrompt> prompts) {
        Long storyId = story.getId();
        return new BookResponse(storyId, version.getId(), story.getTitle(), StoryCardResponse.coverUrl(story),
                StoryImageUrls.frontCover(storyId, version), StoryImageUrls.backCover(storyId, version), pageCount,
                pages.stream().map(page -> toPage(storyId, version.getId(), page)).toList(),
                prompts.stream().map(BookResponse::toPrompt).toList());
    }

    private static Page toPage(Long storyId, Long versionId, StoryPage page) {
        String imageUrl = page.getImageStatus() == ImageStatus.DONE
                ? StoryImageUrls.page(storyId, versionId, page.getPageNo()) : null;
        return new Page(page.getPageNo(), page.getText(), imageUrl, page.getImageStatus(), page.getTextPosition(),
                page.getTextBlocks(), page.getSoundWord());
    }

    private static Prompt toPrompt(GuidePrompt prompt) {
        return new Prompt(prompt.getId(), prompt.getAfterPage(), prompt.getQuestion(), prompt.getParentHint());
    }

    // 본문에 부모 입력이 반영되어 있어 로그에 남기지 않는다
    @Override
    public String toString() {
        return "BookResponse[storyId=" + storyId + ", versionId=" + versionId + "]";
    }
}
