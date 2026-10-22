package com.firstory.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("firstory")
public record FirstoryProperties(Crypto crypto, Story story, Policy policy, Ai ai, Web web, Auth auth,
                                 Generation generation, Storage storage) {

    public record Crypto(String phoneAesKey, String phoneHmacKey) {
    }

    /**
     * @param pageCount 동화 쪽수. DB에 박지 않고 여기서만 정한다
     */
    public record Story(int pageCount) {
    }

    /**
     * @param maxRegenerations  다시 만들기 최대 횟수
     * @param safetyReviseLimit 위기 신호 REVISE가 이 횟수째가 되면 차단한다 (동화 단위)
     */
    // TODO(PM): 다시 만들기 최대 횟수 미정. 임시값 3
    public record Policy(int maxRegenerations, int safetyReviseLimit) {
    }

    /**
     * AI 서비스(FastAPI) 호출 설정.
     *
     * @param mode          http: 진짜 AI 서버를 부른다. fake: 서버 없이 고정된 응답 (local, test)
     * @param internalToken X-Internal-Token 헤더 값. http인데 비어 있으면 기동하지 않는다
     */
    public record Ai(AiMode mode, String baseUrl, String internalToken, Duration connectTimeout,
                     Duration readTimeout) {

        public enum AiMode {
            HTTP,
            FAKE
        }

        public Ai {
            mode = mode == null ? AiMode.HTTP : mode;
            if (mode == AiMode.HTTP && (internalToken == null || internalToken.isBlank())) {
                throw new IllegalStateException(
                        "firstory.ai.mode=http이면 AI_INTERNAL_TOKEN이 필요해요. AI 서버 없이 띄우려면 AI_MODE=fake로 실행해요.");
            }
        }
    }

    /**
     * 쿠키와 출처 설정. 도메인은 코드에 적지 않고 환경 변수로만 받는다.
     *
     * @param cookieDomain   비어 있으면 Domain 속성을 쓰지 않는다 (host-only 쿠키)
     * @param allowedOrigins 비어 있으면 CORS 설정을 등록하지 않는다 (같은 출처 배포)
     */
    public record Web(boolean cookieSecure, String cookieDomain, List<String> allowedOrigins) {

        public Web {
            cookieDomain = cookieDomain == null || cookieDomain.isBlank() ? null : cookieDomain.trim();
            allowedOrigins = allowedOrigins == null ? List.of()
                    : allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        }

        public boolean hasCookieDomain() {
            return cookieDomain != null;
        }
    }

    public record Auth(String rememberMeKey) {
    }

    /**
     * 생성 진행 확인 (GenerationPoller).
     *
     * @param pollEnabled  false면 스케줄러를 등록하지 않는다 (test 프로필은 pollOnce()를 직접 부른다)
     * @param pollInterval AI에 진행 상태를 묻는 간격
     * @param timeout      시작하고 이 시간이 지나도 RUNNING이면 실패로 처리한다
     */
    public record Generation(boolean pollEnabled, Duration pollInterval, Duration timeout) {
    }

    /**
     * @param localRoot 로컬 파일 저장소의 루트 폴더. 그림 파일이 여기에 쌓인다
     */
    public record Storage(String localRoot) {
    }
}
