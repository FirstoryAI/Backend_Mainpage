package com.firstory.domain.story.dto;

import com.firstory.domain.story.entity.Story;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 서재 목록 (LIB-01). page는 0부터.
 */
public record LibraryResponse(List<StoryCardResponse> items, int page, int size, long totalElements,
                              boolean hasNext) {

    public static LibraryResponse of(Page<Story> page) {
        return new LibraryResponse(page.getContent().stream().map(StoryCardResponse::of).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.hasNext());
    }
}
