package com.firstory.domain.user.repository;

import com.firstory.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByKakaoId(String kakaoId);

    boolean existsByKakaoId(String kakaoId);

    /**
     * 같은 이메일로 가장 최근에 탈퇴한 회원. 재가입 때 이용권을 이어 주려고 본다.
     * User의 deleted_at 제한을 피하려고 native로 읽는다.
     */
    @Query(value = """
            SELECT id FROM users WHERE email = :email AND deleted_at IS NOT NULL
            ORDER BY deleted_at DESC, id DESC LIMIT 1""", nativeQuery = true)
    Optional<Long> findLastWithdrawnId(@Param("email") String email);
}
