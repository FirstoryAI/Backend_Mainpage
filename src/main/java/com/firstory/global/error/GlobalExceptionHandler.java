package com.firstory.global.error;

import com.firstory.global.error.ValidationErrorResponse.FieldMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus()).body(ErrorResponse.of(e));
    }

    // 거부된 값(rejectedValue)은 비밀번호나 번호일 수 있어 응답에 담지 않는다
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        return validationFailed(e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldMessage(error.getField(), error.getDefaultMessage()))
                .toList());
    }

    // 쿼리·경로 값의 형식이 맞지 않음 (예: 없는 서재 filter). 받은 값은 응답에 담지 않는다
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ValidationErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validationFailed(List.of(new FieldMessage(e.getName(), ErrorCode.VALIDATION_FAILED.getMessage())));
    }

    // 깨진 JSON, 없는 enum 값, 모르는 Map 키, 타입 불일치. 어느 필드인지만 알려 주고,
    // 받은 값이 들어 있는 예외 메시지는 응답에도 로그에도 쓰지 않는다
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return validationFailed(List.of(RequestBodyErrors.describe(e.getCause())));
    }

    private static ResponseEntity<ValidationErrorResponse> validationFailed(List<FieldMessage> fields) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus()).body(ValidationErrorResponse.of(fields));
    }
}
