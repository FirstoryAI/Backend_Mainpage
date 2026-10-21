package com.firstory.global.error;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API가 낼 수 있는 오류 코드. Swagger 문서의 응답 설명을 ErrorCode의 상태와 메시지로 만든다 (OpenApiConfig).
 * 클래스에 달면 그 컨트롤러의 모든 API에 붙는다. 상태를 바꾸는 요청의 FORBIDDEN(CSRF)은 자동으로 붙는다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiErrorCodes {

    ErrorCode[] value();
}
