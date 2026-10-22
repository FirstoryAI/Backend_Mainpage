package com.firstory.domain.reading.dto;

import com.firstory.domain.reading.entity.DialogueActionType;
import jakarta.validation.constraints.NotNull;

/**
 * @param action TALKED(이야기 나눴어요) 또는 SKIPPED(건너뛰기)
 */
public record DialogueRequest(@NotNull(message = "이야기를 나눴는지 골라 주세요.") DialogueActionType action) {
}
