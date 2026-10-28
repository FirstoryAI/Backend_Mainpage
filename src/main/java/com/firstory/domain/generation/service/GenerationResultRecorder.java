package com.firstory.domain.generation.service;

import com.firstory.domain.generation.entity.GenerationRun;
import com.firstory.domain.generation.entity.StoryFiles;
import com.firstory.domain.generation.entity.StoryPage;
import com.firstory.domain.generation.entity.StoryVersion;
import com.firstory.domain.generation.repository.GenerationRunRepository;
import com.firstory.domain.generation.repository.StoryPageRepository;
import com.firstory.domain.generation.repository.StoryVersionRepository;
import com.firstory.domain.guide.entity.GuidePrompt;
import com.firstory.domain.guide.entity.ReadingGuide;
import com.firstory.domain.guide.repository.GuidePromptRepository;
import com.firstory.domain.guide.repository.ReadingGuideRepository;
import com.firstory.domain.safety.entity.SafetyFlag;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.domain.safety.repository.SafetyFlagRepository;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.global.ai.AiGenerationResult;
import com.firstory.global.ai.SafetyResult;
import com.firstory.global.config.FirstoryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * AI 결과를 버전, 페이지, 가이드로 저장한다 (한 트랜잭션).
 *
 * <ol>
 *   <li>모든 파일 key가 stories/{storyId}/ 아래인지. 아니면 실행 FAILED(INVALID_OUTPUT_PATH). 다른 동화의 그림을 가리키는 것을 막는다</li>
 *   <li>쪽 수가 page-count와 같은지. 아니면 FAILED(INVALID_PAGE_COUNT)</li>
 *   <li>결과 위기 신호가 REVISE, CRISIS면 SafetyFlag(OUTPUT)를 남기고 FAILED(OUTPUT_UNSAFE). 버전은 만들지 않는다.
 *       부모의 REVISE 횟수에는 넣지 않는다. 부모는 다시 시도할 수 있다</li>
 *   <li>버전(parent = 실행의 기준 버전), 페이지, 가이드를 만들고 실행 SUCCEEDED, 스토리 REVIEWING, 새 버전을 보여준다</li>
 * </ol>
 * 실행을 잠그고 RUNNING일 때만 저장한다. 다른 쪽이 먼저 처리했으면 SKIPPED.
 */
@Slf4j
@Service
public class GenerationResultRecorder {

    public enum Outcome {
        SUCCEEDED,
        FAILED,
        // 이미 다른 쪽이 처리함
        SKIPPED
    }

    static final String INVALID_OUTPUT_PATH = "INVALID_OUTPUT_PATH";
    static final String INVALID_PAGE_COUNT = "INVALID_PAGE_COUNT";
    static final String OUTPUT_UNSAFE = "OUTPUT_UNSAFE";
    static final int TITLE_MAX_LENGTH = 100;
    static final String UNKNOWN_CATEGORY = "other";
    static final String MAIN_ROLE = "main";

    private final GenerationRunRepository runRepository;
    private final StoryVersionRepository versionRepository;
    private final StoryPageRepository pageRepository;
    private final ReadingGuideRepository guideRepository;
    private final GuidePromptRepository promptRepository;
    private final SafetyFlagRepository safetyFlagRepository;
    private final Clock clock;
    private final int pageCount;

    public GenerationResultRecorder(GenerationRunRepository runRepository, StoryVersionRepository versionRepository,
                                    StoryPageRepository pageRepository, ReadingGuideRepository guideRepository,
                                    GuidePromptRepository promptRepository, SafetyFlagRepository safetyFlagRepository,
                                    Clock clock, FirstoryProperties properties) {
        this.runRepository = runRepository;
        this.versionRepository = versionRepository;
        this.pageRepository = pageRepository;
        this.guideRepository = guideRepository;
        this.promptRepository = promptRepository;
        this.safetyFlagRepository = safetyFlagRepository;
        this.clock = clock;
        this.pageCount = properties.story().pageCount();
    }

    @Transactional
    public Outcome record(Long runId, AiGenerationResult result) {
        GenerationRun run = runRepository.findByIdForUpdate(runId).orElse(null);
        if (run == null || !run.isRunning()) {
            return Outcome.SKIPPED;
        }
        Story story = run.getStory();
        Long storyId = story.getId();

        if (!result.fileKeys().stream().allMatch(key -> StoryFiles.belongsTo(key, storyId))) {
            log.warn("AI 결과의 파일 경로가 이 동화 것이 아니어서 저장하지 않아요: run_id={}, story_id={}", runId, storyId);
            return fail(run, story, INVALID_OUTPUT_PATH);
        }
        if (!hasAllPages(result.pages())) {
            log.warn("AI 결과의 쪽 수가 맞지 않아요: run_id={}, expected={}, actual={}",
                    runId, pageCount, result.pages().size());
            return fail(run, story, INVALID_PAGE_COUNT);
        }
        SafetyResult safety = result.safety();
        if (safety.level().isRecorded()) {
            recordUnsafeOutput(story, safety);
            log.info("완성 본문에 위기 신호가 있어 보여 주지 않아요: run_id={}, level={}", runId, safety.level());
            return fail(run, story, OUTPUT_UNSAFE);
        }

        int versionNo = versionRepository.findTopByStoryIdOrderByVersionNoDesc(storyId)
                .map(StoryVersion::getVersionNo).orElse(0) + 1;
        StoryVersion version = versionRepository.save(StoryVersion.create(story, run, versionNo, run.getBaseVersion(),
                title(result.title()), result.checkpointId(), result.design(), characterSheetKey(result),
                result.coverPath()));
        savePages(version, result.pages());
        saveGuide(version, result.guide());

        AiGenerationResult.Cost cost = result.cost();
        run.succeed(cost.inputTokens(), cost.outputTokens(), cost.imageCount(), clock.instant());
        story.advanceGenerationTo(StoryStatus.REVIEWING);
        story.showVersion(version);
        return Outcome.SUCCEEDED;
    }

    private boolean hasAllPages(List<AiGenerationResult.Page> pages) {
        if (pages.size() != pageCount || pages.stream().anyMatch(page -> page.text() == null)) {
            return false;
        }
        return pages.stream().map(AiGenerationResult.Page::pageNo).collect(Collectors.toSet())
                .equals(IntStream.rangeClosed(1, pageCount).boxed().collect(Collectors.toSet()));
    }

    private void recordUnsafeOutput(Story story, SafetyResult safety) {
        // 순번은 같은 level의 모든 기록(부모 입력 포함)에 이어서 센다 (UNIQUE story, level, occurrence_no)
        int occurrenceNo = (int) safetyFlagRepository.countByStoryIdAndLevel(story.getId(), safety.level()) + 1;
        String category = safety.category() == null || safety.category().isBlank() ? UNKNOWN_CATEGORY
                : safety.category();
        safetyFlagRepository.save(SafetyFlag.record(story, SafetySource.OUTPUT, safety.level(), null, category,
                safety.reason(), occurrenceNo, clock.instant()));
    }

    private void savePages(StoryVersion version, List<AiGenerationResult.Page> pages) {
        pages.stream().sorted(Comparator.comparingInt(AiGenerationResult.Page::pageNo)).forEach(page -> {
            StoryPage storyPage = StoryPage.create(version, page.pageNo(), page.text(), page.imagePrompt(),
                    page.textPosition());
            if (page.imageDone()) {
                storyPage.imageDone(page.imagePath());
            } else {
                storyPage.imageFailed();
            }
            pageRepository.save(storyPage);
        });
    }

    private void saveGuide(StoryVersion version, AiGenerationResult.Guide guide) {
        if (guide == null) {
            return;
        }
        ReadingGuide readingGuide = guideRepository.save(ReadingGuide.create(version, guide.synopsis(),
                guide.beforeIntent(), guide.beforeTips(), guide.bridgeQuestion(), guide.ifUndecided()));
        List<AiGenerationResult.Prompt> prompts = guide.prompts().stream()
                .filter(prompt -> prompt.afterPage() >= 1 && prompt.afterPage() <= pageCount)
                .toList();
        if (prompts.size() != guide.prompts().size()) {
            log.warn("쪽 범위를 벗어난 대화 질문을 뺐어요: version_id={}", version.getId());
        }
        for (int i = 0; i < prompts.size(); i++) {
            AiGenerationResult.Prompt prompt = prompts.get(i);
            promptRepository.save(GuidePrompt.create(readingGuide, i + 1, prompt.afterPage(), prompt.text(),
                    prompt.purpose(), prompt.parentHint()));
        }
    }

    private Outcome fail(GenerationRun run, Story story, String errorCode) {
        run.fail(errorCode, clock.instant());
        story.changeStatus(StoryStatus.FAILED);
        return Outcome.FAILED;
    }

    private static String title(String title) {
        String stripped = title == null ? "" : title.strip();
        if (stripped.codePointCount(0, stripped.length()) <= TITLE_MAX_LENGTH) {
            return stripped;
        }
        return stripped.substring(0, stripped.offsetByCodePoints(0, TITLE_MAX_LENGTH));
    }

    private static String characterSheetKey(AiGenerationResult result) {
        return result.characterSheets().stream()
                .filter(sheet -> MAIN_ROLE.equals(sheet.role()))
                .findFirst()
                .or(() -> result.characterSheets().stream().findFirst())
                .map(AiGenerationResult.CharacterSheet::path)
                .orElse(null);
    }
}
