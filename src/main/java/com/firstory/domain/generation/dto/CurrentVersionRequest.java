package com.firstory.domain.generation.dto;

import jakarta.validation.constraints.NotNull;

public record CurrentVersionRequest(@NotNull(message = "보여 줄 버전을 골라 주세요.") Long versionId) {
}
