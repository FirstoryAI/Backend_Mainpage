package com.firstory.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record SmsNotifyRequest(@NotNull(message = "동의 여부를 선택해 주세요.") Boolean agreed) {
}
