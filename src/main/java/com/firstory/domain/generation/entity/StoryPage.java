package com.firstory.domain.generation.entity;

import com.firstory.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Objects;

@Getter
@Entity
@Table(name = "story_pages",
        uniqueConstraints = @UniqueConstraint(columnNames = {"version_id", "page_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryPage extends BaseTimeEntity {

    // 마지막 쪽은 설정값(firstory.story.page-count)이다. DB와 엔티티는 1 이상만 검사한다
    public static final int FIRST_PAGE = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private StoryVersion version;

    @Column(name = "page_no", nullable = false)
    private int pageNo;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "image_prompt", columnDefinition = "text")
    private String imagePrompt;

    @Column(name = "image_key")
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_status", nullable = false, length = 20)
    private ImageStatus imageStatus;

    @Column(name = "audio_key")
    private String audioKey;

    /** 쪽 음성(해설·등장인물 목소리 mp3) 상태. 음성이 없는 쪽과 이전 동화는 null */
    @Enumerated(EnumType.STRING)
    @Column(name = "audio_status", length = 20)
    private AudioStatus audioStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "text_position", nullable = false, length = 10)
    private TextPosition textPosition;

    /** 글 덩어리 배치. 이전 쪽, 그림이 실패한 쪽, 형식이 어긋난 쪽은 null (그때는 textPosition으로 그린다) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "text_blocks", columnDefinition = "jsonb")
    private List<PageTextBlock> textBlocks;

    /** 그림 위 의성어·의태어 조각과 색. 옛 쪽, 별표 말이 없는 쪽, 그림이 실패한 쪽, 형식이 어긋난 쪽은 null */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sound_word", columnDefinition = "jsonb")
    private PageSoundWord soundWord;

    /** 저장된 의성어. 지금 모양으로 읽지 못한 값(11/16 전 옛 모양 등)은 null로 내린다 */
    public PageSoundWord getSoundWord() {
        return PageSoundWord.validated(soundWord);
    }

    private StoryPage(StoryVersion version, int pageNo, String text, String imagePrompt, TextPosition textPosition,
                      List<PageTextBlock> textBlocks, PageSoundWord soundWord, String imageKey, ImageStatus imageStatus,
                      String audioKey, AudioStatus audioStatus) {
        if (pageNo < FIRST_PAGE) {
            throw new IllegalArgumentException("쪽 번호는 1 이상이어야 해요: " + pageNo);
        }
        this.version = Objects.requireNonNull(version);
        this.pageNo = pageNo;
        this.text = Objects.requireNonNull(text);
        this.imagePrompt = imagePrompt;
        this.textPosition = Objects.requireNonNull(textPosition);
        this.textBlocks = textBlocks;
        this.soundWord = soundWord;
        this.imageKey = imageKey;
        this.imageStatus = Objects.requireNonNull(imageStatus);
        this.audioKey = audioKey;
        this.audioStatus = audioStatus;
    }

    public static StoryPage create(StoryVersion version, int pageNo, String text, String imagePrompt,
                                   TextPosition textPosition, List<PageTextBlock> textBlocks,
                                   PageSoundWord soundWord) {
        return new StoryPage(version, pageNo, text, imagePrompt, textPosition, textBlocks, soundWord, null,
                ImageStatus.PENDING, null, null);
    }

    /**
     * 미리보기에서 부모가 옮긴 의성어 조각 자리를 저장한다 (#100). 이 버전의 쪽에만 남는다.
     */
    public void moveSoundWord(PageSoundWord moved) {
        this.soundWord = Objects.requireNonNull(moved);
    }

    public void imageDone(String imageKey) {
        this.imageKey = Objects.requireNonNull(imageKey);
        this.imageStatus = ImageStatus.DONE;
    }

    public void imageFailed() {
        this.imageStatus = ImageStatus.FAILED;
    }

    public void audioDone(String audioKey) {
        this.audioKey = Objects.requireNonNull(audioKey);
        this.audioStatus = AudioStatus.DONE;
    }

    public void audioFailed() {
        this.audioStatus = AudioStatus.FAILED;
    }

    /**
     * 글과 이미지·음성 키를 그대로 복사한 새 페이지.
     * "그림만 다시" 때 바뀌지 않은 쪽은 S3 파일을 복사하지 않고 키만 공유한다.
     */
    public StoryPage copyTo(StoryVersion target) {
        return new StoryPage(target, pageNo, text, imagePrompt, textPosition, textBlocks, soundWord, imageKey,
                imageStatus, audioKey, audioStatus);
    }
}
