package com.firstory.global.ai;

import com.firstory.domain.generation.entity.TextPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiGenerationResultTest {

    @Test
    void 파일_key에_앞뒤표지도_들어가_다른_동화_경로면_저장_전에_걸린다() {
        AiGenerationResult result = new AiGenerationResult(1, "cp", "제목", null, "stories/1/runs/1/images/cover.png",
                "stories/2/runs/1/images/front-cover.png", "stories/1/runs/1/images/back-cover.png", List.of(),
                List.of(), null, null, null);

        assertThat(result.fileKeys()).contains("stories/2/runs/1/images/front-cover.png",
                "stories/1/runs/1/images/back-cover.png");
    }

    @Test
    void 파일_key에_쪽_음성도_들어간다() {
        AiGenerationResult.Page page = new AiGenerationResult.Page(1, "글", "p", TextPosition.BOTTOM,
                "stories/1/runs/1/images/page-01.png", "done", null, null,
                "stories/1/runs/1/audio/page-01.mp3", "done");
        AiGenerationResult result = new AiGenerationResult(1, "cp", "제목", null, null, null, null, List.of(),
                List.of(page), null, null, null);

        assertThat(result.fileKeys()).containsExactly("stories/1/runs/1/images/page-01.png",
                "stories/1/runs/1/audio/page-01.mp3");
        assertThat(page.audioDone()).isTrue();
        assertThat(page.audioFailed()).isFalse();
        assertThat(new AiGenerationResult.Page(1, "글", "p", TextPosition.BOTTOM, null, "failed", null, null,
                null, "failed").audioFailed()).isTrue();
    }

    @Test
    void 앞뒤표지가_없으면_파일_key에서_빠진다() {
        AiGenerationResult result = new AiGenerationResult(1, "cp", "제목", null, null, null, null, List.of(),
                List.of(), null, null, null);

        assertThat(result.fileKeys()).isEmpty();
    }
}
