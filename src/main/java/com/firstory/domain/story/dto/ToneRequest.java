package com.firstory.domain.story.dto;

import com.firstory.domain.story.entity.Tone;
import jakarta.validation.constraints.NotNull;

public record ToneRequest(@NotNull(message = "이야기 분위기를 골라 주세요.") Tone tone) {
}
