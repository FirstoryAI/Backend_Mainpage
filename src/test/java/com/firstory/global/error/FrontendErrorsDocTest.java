package com.firstory.global.error;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프론트 문서(docs/frontend/errors.md)의 오류 표가 ErrorCode와 어긋나지 않게 한다.
 * 코드를 추가하거나 지우거나 HTTP 상태를 바꾸면 문서도 함께 고쳐야 통과한다.
 */
class FrontendErrorsDocTest {

    static final Path DOC = Path.of("docs/frontend/errors.md");
    // | `CODE` | 404 | 메시지 | ...
    static final Pattern ROW = Pattern.compile("^\\| `([A-Z_]+)` \\| (\\d{3}) \\| ([^|]*) \\|", Pattern.MULTILINE);

    @Test
    void 모든_ErrorCode가_문서에_있고_HTTP_상태와_메시지가_같다() throws IOException {
        List<Row> rows = rows();

        assertThat(rows).extracting(Row::code)
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
        for (Row row : rows) {
            ErrorCode errorCode = ErrorCode.valueOf(row.code());
            assertThat(row.status()).as(row.code()).isEqualTo(errorCode.getStatus().value());
            assertThat(row.message()).as(row.code()).isEqualTo(errorCode.getMessage());
        }
    }

    @Test
    void 문서의_코드는_한_번씩만() throws IOException {
        List<String> codes = rows().stream().map(Row::code).toList();

        Set<String> unique = codes.stream().collect(Collectors.toSet());
        assertThat(codes).hasSameSizeAs(unique);
    }

    private static List<Row> rows() throws IOException {
        String doc = Files.readString(DOC, StandardCharsets.UTF_8);
        Matcher matcher = ROW.matcher(doc);
        List<Row> rows = new ArrayList<>();
        while (matcher.find()) {
            rows.add(new Row(matcher.group(1), Integer.parseInt(matcher.group(2)), matcher.group(3).strip()));
        }
        return rows;
    }

    record Row(String code, int status, String message) {
    }
}
