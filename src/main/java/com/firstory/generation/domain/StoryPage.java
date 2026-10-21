package com.firstory.generation.domain;

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

import java.util.Objects;

@Getter
@Entity
@Table(name = "story_pages",
        uniqueConstraints = @UniqueConstraint(columnNames = {"version_id", "page_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryPage extends BaseTimeEntity {

    public static final int FIRST_PAGE = 1;
    public static final int LAST_PAGE = 10;

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

    private StoryPage(StoryVersion version, int pageNo, String text, String imagePrompt,
                      String imageKey, ImageStatus imageStatus) {
        if (pageNo < FIRST_PAGE || pageNo > LAST_PAGE) {
            throw new IllegalArgumentException("쪽 번호는 1~10 사이여야 해요: " + pageNo);
        }
        this.version = Objects.requireNonNull(version);
        this.pageNo = pageNo;
        this.text = Objects.requireNonNull(text);
        this.imagePrompt = imagePrompt;
        this.imageKey = imageKey;
        this.imageStatus = Objects.requireNonNull(imageStatus);
    }

    public static StoryPage create(StoryVersion version, int pageNo, String text, String imagePrompt) {
        return new StoryPage(version, pageNo, text, imagePrompt, null, ImageStatus.PENDING);
    }

    public void imageDone(String imageKey) {
        this.imageKey = Objects.requireNonNull(imageKey);
        this.imageStatus = ImageStatus.DONE;
    }

    public void imageFailed() {
        this.imageStatus = ImageStatus.FAILED;
    }

    /**
     * 글과 이미지 키를 그대로 복사한 새 페이지.
     * "그림만 다시" 때 바뀌지 않은 쪽은 S3 파일을 복사하지 않고 키만 공유한다.
     */
    public StoryPage copyTo(StoryVersion target) {
        return new StoryPage(target, pageNo, text, imagePrompt, imageKey, imageStatus);
    }
}
