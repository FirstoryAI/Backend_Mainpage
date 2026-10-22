package com.firstory.domain.reading.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @param page 지금 보고 있는 쪽 (1~page-count)
 */
public record ProgressRequest(@NotNull(message = "쪽 번호를 보내 주세요.") Integer page) {
}
