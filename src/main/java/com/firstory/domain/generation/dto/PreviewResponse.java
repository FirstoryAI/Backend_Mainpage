package com.firstory.domain.generation.dto;

import com.firstory.domain.generation.entity.AudioStatus;
import com.firstory.domain.generation.entity.ImageStatus;
import com.firstory.domain.generation.entity.PageSoundWord;
import com.firstory.domain.generation.entity.PageTextBlock;
import com.firstory.domain.generation.entity.StoryPage;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.generation.entity.TextPosition;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 미리보기 (REV-01). 그림은 imageUrl(그림 API)로 받는다. 파일 key나 저장 경로는 넣지 않는다.
 *
 * @param characterSheetUrl   캐릭터 시트가 없으면 null
 * @param coverUrl            표지(제목이 들어간 정사각형). 표지가 없으면 캐릭터 시트, 둘 다 없으면 null
 * @param frontCoverUrl       책 맨 앞 앞표지(정사각형 표지와 같은 그림, 옛 버전은 가로). 없으면(이전 버전, 표지 실패) null
 * @param backCoverUrl        책 맨 뒤 뒤표지(정사각형, 글자 없음, 옛 버전은 가로). 없으면 null
 * @param regenerateRemaining 남은 다시 만들기 횟수
 */
public record PreviewResponse(Long storyId, StoryStatus status, VersionSummary version, String characterSheetUrl,
                              String coverUrl, String frontCoverUrl, String backCoverUrl, List<Page> pages,
                              int regenerateRemaining) {

    public static final String TEXT_BLOCKS_EXAMPLE = "[{\"paragraphs\": [[\"유치원 그림 시간이었어요.\", "
            + "\"토끼 라라는 분홍 앞치마를 입었어요.\"]], \"x\": 5.0, \"y\": 7.0, \"color\": \"DARK\"}, "
            + "{\"paragraphs\": [[\"라라는 고양이 미나에게 꽃을 보여 주고 싶었어요.\"]], \"x\": 55.0, \"y\": 7.0, "
            + "\"color\": \"LIGHT\"}]";

    public static final String SOUND_WORD_EXAMPLE = "{\"text\": \"데굴데굴\", \"color\": \"#E03E4E\", \"pieces\": ["
            + "{\"text\": \"데굴\", \"x\": 18.2, \"y\": 46.0, \"side\": \"LEFT\"}, "
            + "{\"text\": \"데굴\", \"x\": 37.9, \"y\": 46.0, \"side\": \"RIGHT\"}]}";

    public record VersionSummary(Long id, int versionNo, String title) {
    }

    /**
     * @param imageUrl     그림이 실패한 쪽(FAILED)은 null
     * @param textPosition 옛 화면용 글 자리. textBlocks가 null일 때만 이걸로 그린다
     * @param textBlocks   글 덩어리 배치(1~2개). 옛 동화와 그림이 실패한 쪽은 null
     * @param soundWord    의성어·의태어 조각(1~2개, 조각 가운데 x·y %)과 색 #RRGGBB. 옛 동화·별표 말 없는 쪽·그림 실패 쪽은 null
     * @param audioStatus  쪽 음성 상태. 음성이 없는 쪽·옛 동화는 null
     * @param audioUrl     쪽 음성(해설·등장인물 목소리 mp3) 주소. DONE일 때만, 아니면 null
     */
    public record Page(int pageNo, String text, ImageStatus imageStatus, String imageUrl,
                       TextPosition textPosition,
                       @Schema(example = TEXT_BLOCKS_EXAMPLE) List<PageTextBlock> textBlocks,
                       @Schema(example = SOUND_WORD_EXAMPLE) PageSoundWord soundWord,
                       AudioStatus audioStatus, String audioUrl) {
    }

    public static PreviewResponse of(Story story, StoryVersion version, List<StoryPage> pages,
                                     int regenerateRemaining) {
        Long storyId = story.getId();
        return new PreviewResponse(storyId, story.getStatus(),
                new VersionSummary(version.getId(), version.getVersionNo(), version.getTitle()),
                version.getCharacterSheetKey() == null ? null
                        : StoryImageUrls.characterSheet(storyId, version.getId()),
                StoryImageUrls.coverOrSheet(storyId, version),
                StoryImageUrls.frontCover(storyId, version), StoryImageUrls.backCover(storyId, version),
                pages.stream().map(page -> pageOf(storyId, version.getId(), page)).toList(),
                regenerateRemaining);
    }

    private static Page pageOf(Long storyId, Long versionId, StoryPage page) {
        String imageUrl = page.getImageStatus() == ImageStatus.DONE
                ? StoryImageUrls.page(storyId, versionId, page.getPageNo()) : null;
        return new Page(page.getPageNo(), page.getText(), page.getImageStatus(), imageUrl, page.getTextPosition(),
                page.getTextBlocks(), page.getSoundWord(), page.getAudioStatus(),
                page.getAudioStatus() == AudioStatus.DONE
                        ? StoryImageUrls.pageAudio(storyId, versionId, page.getPageNo()) : null);
    }

    // 본문에 부모 입력이 반영되어 있어 로그에 남기지 않는다
    @Override
    public String toString() {
        return "PreviewResponse[storyId=" + storyId + ", status=" + status + "]";
    }
}
