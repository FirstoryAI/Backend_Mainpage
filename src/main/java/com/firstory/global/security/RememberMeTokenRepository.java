package com.firstory.global.security;

import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;

import javax.sql.DataSource;

/**
 * persistent_logins 테이블(Flyway V2)을 쓰는 remember-me 토큰 저장소.
 * 로그아웃한 기기의 토큰만 지울 수 있도록 series 단위 삭제를 더했다.
 */
public class RememberMeTokenRepository extends JdbcTokenRepositoryImpl {

    public RememberMeTokenRepository(DataSource dataSource) {
        setDataSource(dataSource);
        setCreateTableOnStartup(false);
    }

    public void removeSeries(String series) {
        getJdbcTemplate().update("DELETE FROM persistent_logins WHERE series = ?", series);
    }
}
