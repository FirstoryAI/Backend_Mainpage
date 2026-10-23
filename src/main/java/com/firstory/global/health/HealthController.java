package com.firstory.global.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포 플랫폼(Railway) 헬스체크용. 로그인 없이 부를 수 있고 DB나 AI를 확인하지 않는다.
 * 서버 프로세스가 요청을 받을 수 있으면 200이다 (DB 연결은 기동할 때 Flyway가 이미 확인한다).
 */
@Tag(name = "상태 확인")
@RestController
public class HealthController {

    static final HealthResponse OK = new HealthResponse("ok");

    @Operation(summary = "상태 확인", description = "로그인 불필요. 서버가 떠 있으면 200 {\"status\": \"ok\"}")
    @GetMapping("/api/health")
    public HealthResponse health() {
        return OK;
    }

    public record HealthResponse(@Schema(example = "ok") String status) {
    }
}
