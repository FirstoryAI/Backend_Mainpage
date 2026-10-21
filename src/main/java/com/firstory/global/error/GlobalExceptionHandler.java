package com.firstory.global.error;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        List<ValidationErrorResponse.FieldMessage> fields = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationErrorResponse.FieldMessage(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus()).body(ValidationErrorResponse.of(fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus()).body(ValidationErrorResponse.of(List.of()));
    }
}
