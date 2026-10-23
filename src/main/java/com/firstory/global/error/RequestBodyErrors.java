package com.firstory.global.error;

import org.springframework.util.ReflectionUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 요청 본문을 읽지 못했을 때(깨진 JSON, 없는 enum 값, 모르는 Map 키, 타입 불일치) 어느 필드가 문제인지 알려 준다.
 * <p>
 * 필드 이름은 Jackson이 남긴 경로에서 DTO의 필드 이름과 목록 순번만 쓴다. <b>요청 원문은 응답에 담지 않는다.</b>
 * Map의 키는 사용자가 보낸 값이라 경로에 넣지 않고 Map 필드 이름에서 멈춘다 (예: consents).
 * Jackson의 예외 메시지에는 받은 값이 들어 있으므로 응답에도 로그에도 쓰지 않는다.
 */
final class RequestBodyErrors {

    static final String BODY = "body";
    static final String MISSING_BODY = "요청 내용이 없어요.";
    static final String BROKEN_JSON = "JSON 형식이 맞지 않아요.";
    static final String UNKNOWN_KEY = "알 수 없는 항목이 있어요.";
    static final String UNKNOWN_CHOICE = "고를 수 없는 값이에요.";
    static final String WRONG_TYPE = "형식이 맞지 않아요.";

    private RequestBodyErrors() {
    }

    static ValidationErrorResponse.FieldMessage describe(Throwable cause) {
        JacksonException jackson = findJackson(cause);
        if (jackson == null) {
            return new ValidationErrorResponse.FieldMessage(BODY, MISSING_BODY);
        }
        Path path = path(jackson.getPath());
        String field = path.name().isEmpty() ? BODY : path.name();
        if (jackson instanceof StreamReadException) {
            return new ValidationErrorResponse.FieldMessage(field, BROKEN_JSON);
        }
        if (path.endsAtMapField()) {
            // Map 값이 아니라 키를 읽지 못했다 (값이면 경로가 Map 안으로 이어진다)
            return new ValidationErrorResponse.FieldMessage(field, UNKNOWN_KEY);
        }
        if (jackson instanceof InvalidFormatException format && format.getTargetType() != null
                && format.getTargetType().isEnum()) {
            return new ValidationErrorResponse.FieldMessage(field, UNKNOWN_CHOICE);
        }
        return new ValidationErrorResponse.FieldMessage(field, WRONG_TYPE);
    }

    private static JacksonException findJackson(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof JacksonException jackson) {
                return jackson;
            }
        }
        return null;
    }

    /**
     * @param name          profile.traits[1]처럼 DTO 필드 이름과 목록 순번만 이은 경로
     * @param endsAtMapField 경로가 Map 필드에서 끝났고 그 안으로 들어가지 않았다
     */
    private record Path(String name, boolean endsAtMapField) {
    }

    private static Path path(List<JacksonException.Reference> references) {
        StringBuilder name = new StringBuilder();
        boolean lastIsMapField = false;
        for (JacksonException.Reference reference : references) {
            Object from = reference.from();
            if (from instanceof Map<?, ?>) {
                // 키는 사용자가 보낸 값이다. 여기서 멈춘다
                return new Path(name.toString(), false);
            }
            if (reference.getIndex() >= 0 && (from instanceof Collection<?> || isArray(from))) {
                name.append('[').append(reference.getIndex()).append(']');
                lastIsMapField = false;
                continue;
            }
            String property = reference.getPropertyName();
            Field field = property == null ? null : declaredField(from, property);
            if (field == null) {
                // DTO에 없는 이름이면 사용자가 보낸 것일 수 있어 쓰지 않는다
                return new Path(name.toString(), false);
            }
            if (!name.isEmpty()) {
                name.append('.');
            }
            name.append(property);
            lastIsMapField = Map.class.isAssignableFrom(field.getType());
        }
        return new Path(name.toString(), lastIsMapField);
    }

    private static Field declaredField(Object from, String property) {
        Class<?> owner = from instanceof Class<?> type ? type : from == null ? null : from.getClass();
        return owner == null ? null : ReflectionUtils.findField(owner, property);
    }

    private static boolean isArray(Object from) {
        return from != null && (from.getClass().isArray() || from instanceof Class<?> type && type.isArray());
    }
}
