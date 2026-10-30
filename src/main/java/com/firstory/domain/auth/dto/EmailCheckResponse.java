package com.firstory.domain.auth.dto;

/**
 * @param available 가입할 수 있으면 true, 이미 가입된 이메일이면 false
 */
public record EmailCheckResponse(boolean available) {
}
