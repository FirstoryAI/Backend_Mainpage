package com.firstory.support;

import com.firstory.global.config.JpaConfig;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Flyway 마이그레이션(V1~V5)을 적용한 PostgreSQL 16에서 도는 Repository 테스트의 기반.
 * jsonb와 부분 인덱스가 있어 H2를 쓰지 않는다.
 * 컨테이너는 모든 테스트 클래스가 함께 쓴다 (Spring 컨텍스트 캐시와 수명을 맞추기 위함).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
public abstract class RepositoryTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }
}
