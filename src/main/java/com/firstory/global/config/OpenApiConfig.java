package com.firstory.global.config;

import com.firstory.global.error.ApiErrorCodes;
import com.firstory.global.error.ErrorCode;
import com.firstory.global.error.ErrorResponse;
import com.firstory.global.error.ValidationErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Swagger 문서 설정. springdoc은 local 프로필에서만 켠다 (application.yml).
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

    private static final String AUTH_TAG = "인증";
    private static final List<Class<?>> ERROR_SCHEMAS = List.of(ErrorResponse.class, ValidationErrorResponse.class);

    @Bean
    public OpenAPI firstoryOpenApi() {
        // 오류 응답 스키마는 @ApiErrorCodes가 $ref로만 가리켜서 springdoc이 스스로 등록하지 않는다.
        // read()는 그 클래스만 돌려주므로 안쪽 타입(FieldMessage)까지 담는 readAll()을 쓴다.
        // schemas(map)는 덮어쓰므로 합쳐서 한 번에 넣는다
        Map<String, Schema> schemas = new TreeMap<>();
        for (Class<?> errorType : ERROR_SCHEMAS) {
            schemas.putAll(ModelConverters.getInstance().readAll(errorType));
        }
        Components components = new Components().schemas(schemas);
        return new OpenAPI()
                .info(new Info().title("FIRSTORY API").version("v1").description("""
                        로그인은 서버 세션(FSESSION 쿠키)으로 유지한다. Swagger에서 쓰는 순서:
                        1. `GET /api/auth/csrf`를 먼저 실행해 XSRF-TOKEN 쿠키를 받는다 (로그아웃한 뒤에도 다시 실행).
                        2. 가입하거나 `POST /api/auth/login`으로 로그인한다. 이후 요청은 브라우저 쿠키로 인증된다.
                        3. POST/PUT/PATCH/DELETE는 Swagger UI가 X-XSRF-TOKEN 헤더를 자동으로 붙인다.
                        local 프로필에서는 인증번호가 서버 로그에 `[SMS] 010-****-1234 ← ...`로 찍힌다."""))
                .components(components);
    }

    /**
     * @ApiErrorCodes를 응답 설명으로 바꾼다. 같은 상태 코드는 한 응답에 묶고 "코드: 메시지"로 나열한다.
     */
    @Bean
    public OperationCustomizer errorCodeCustomizer() {
        return (operation, handlerMethod) -> {
            Set<ErrorCode> codes = new LinkedHashSet<>();
            addCodes(codes, AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ApiErrorCodes.class));
            addCodes(codes, AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ApiErrorCodes.class));
            if (changesState(handlerMethod)) {
                codes.add(ErrorCode.FORBIDDEN);
            }
            addResponses(operation, codes);
            return operation;
        };
    }

    /**
     * 로그아웃은 컨트롤러가 아니라 Spring Security의 LogoutFilter가 처리해서 springdoc이 찾지 못한다. 직접 적는다.
     */
    @Bean
    public OpenApiCustomizer logoutPathCustomizer() {
        return openApi -> {
            Operation logout = new Operation()
                    .addTagsItem(AUTH_TAG)
                    .summary("로그아웃")
                    .description("세션을 끝내고 이 기기의 로그인 상태 유지 토큰을 지운다. FSESSION, FREMEMBER, XSRF-TOKEN 쿠키가 지워지므로 "
                            + "다음 요청 전에 GET /api/auth/csrf를 다시 부른다. 로그인하지 않은 상태에서 불러도 204.")
                    .responses(new ApiResponses().addApiResponse("204", new ApiResponse().description("로그아웃됨")));
            addResponses(logout, Set.of(ErrorCode.FORBIDDEN));
            openApi.path("/api/auth/logout", new PathItem().post(logout));
            if (openApi.getTags() == null || openApi.getTags().stream().noneMatch(t -> AUTH_TAG.equals(t.getName()))) {
                openApi.addTagsItem(new Tag().name(AUTH_TAG));
            }
        };
    }

    private static void addCodes(Set<ErrorCode> codes, ApiErrorCodes annotation) {
        if (annotation != null) {
            codes.addAll(Arrays.asList(annotation.value()));
        }
    }

    private static boolean changesState(HandlerMethod handlerMethod) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequestMapping.class);
        return mapping != null && Arrays.stream(mapping.method()).anyMatch(method -> method != RequestMethod.GET);
    }

    private static void addResponses(Operation operation, Set<ErrorCode> codes) {
        if (codes.isEmpty()) {
            return;
        }
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        Map<Integer, Set<ErrorCode>> byStatus = codes.stream().collect(Collectors.groupingBy(
                code -> code.getStatus().value(), TreeMap::new, Collectors.toCollection(LinkedHashSet::new)));
        byStatus.forEach((status, sameStatus) -> {
            String description = sameStatus.stream()
                    .map(code -> "`" + code.name() + "`: " + describe(code))
                    .collect(Collectors.joining("\n\n"));
            boolean validation = sameStatus.contains(ErrorCode.VALIDATION_FAILED);
            Schema<?> schema = new Schema<>().$ref("#/components/schemas/"
                    + (validation ? ValidationErrorResponse.class : ErrorResponse.class).getSimpleName());
            operation.getResponses().addApiResponse(String.valueOf(status), new ApiResponse()
                    .description(description)
                    .content(new Content().addMediaType("application/json", new MediaType().schema(schema))));
        });
    }

    private static String describe(ErrorCode code) {
        return switch (code) {
            case FORBIDDEN -> code.getMessage() + " (X-XSRF-TOKEN 헤더가 없거나 맞지 않을 때도 이 응답)";
            case VALIDATION_FAILED -> code.getMessage() + " (fields에 필드별 메시지)";
            case CUSTOM_VALUE_TOO_LONG -> code.getMessage() + " ({n}은 항목별 최대 글자 수)";
            default -> code.getMessage();
        };
    }
}
