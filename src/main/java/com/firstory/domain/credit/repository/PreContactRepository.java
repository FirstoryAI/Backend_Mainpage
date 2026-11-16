package com.firstory.domain.credit.repository;

import com.firstory.domain.credit.entity.PreContact;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PreContactRepository extends JpaRepository<PreContact, Long> {

    Optional<PreContact> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PreContact p where p.email = :email")
    Optional<PreContact> findByEmailForUpdate(@Param("email") String email);
}
