package com.firstory.domain.reading.dto;

import com.firstory.domain.generation.dto.StoryImageUrls;
import com.firstory.domain.generation.entity.ImageStatus;
import com.firstory.domain.generation.entity.StoryPage;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.guide.entity.GuidePrompt;
import com.firstory.domain.story.dto.StoryCardResponse;
import com.firstory.domain.story.entity.Story;

import java.util.List;

/**
 * 함께 읽기 화면 (RD-01). 확정된 버전의 표지와 쪽, 쪽 사이에 나올 대화 질문.
 * 그림은 imageUrl(그림 API)로 받는다. 파일 key나 저장 경로는 넣지 않는다.
 *
 * @param coverUrl 캐릭터 시트가 없으면 null
 * @param prompts  afterPage 쪽을 읽은 뒤에 보여준다 (RD-02)
 */
public record BookResponse(Long storyId, Long versionId, String title, String coverUrl, int pageCount,
                           List<Page> pages, List<Prompt> prompts) {

    /**
     * @param imageUrl 그림이 실패한 쪽(FAILED)은 null
     */
    public record Page(int pageNo, String text, String imageUrl, ImageStatus imageStatus) {
    }

    public record Prompt(Long id, int afterPage, String question, String parentHint) {
    }

    public static BookResponse of(Story story, StoryVersion version, int pageCount, List<StoryPage> pages,
                                  List<GuidePrompt> prompts) {
        Long storyId = story.getId();
        return new BookResponse(storyId, version.getId(), story.getTitle(), StoryCardResponse.coverUrl(story),
                pageCount,
                pages.stream().map(page -> new Page(page.getPageNo(), page.getText(),
                        page.getImageStatus() == ImageStatus.DONE
                                ? StoryImageUrls.page(storyId, version.getId(), page.getPageNo()) : null,
                        page.getImageStatus())).toList(),
                prompts.stream().map(prompt -> new Prompt(prompt.getId(), prompt.getAfterPage(), prompt.getQuestion(),
                        prompt.getParentHint())).toList());
    }

    // 본문에 부모 입력이 반영되어 있어 로그에 남기지 않는다
    @Override
    public String toString() {
        return "BookResponse[storyId=" + storyId + ", versionId=" + versionId + "]";
    }
}
