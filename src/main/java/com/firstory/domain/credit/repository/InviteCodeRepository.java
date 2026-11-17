package com.firstory.domain.credit.repository;

import com.firstory.domain.credit.entity.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    Optional<InviteCode> findByCode(String code);

    /**
     * 자리가 남았고 마감 전이면 사용 인원을 1 올린다. 1이면 자리를 얻었다. 동시에 가입해도 인원을 넘지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update InviteCode c set c.usedCount = c.usedCount + 1, c.updatedAt = :now
            where c.id = :id and c.usedCount < c.maxUses and c.closesAt > :now""")
    int claim(@Param("id") Long id, @Param("now") Instant now);
}
