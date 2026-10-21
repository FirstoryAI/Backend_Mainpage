package com.firstory.user.repository;

import com.firstory.user.domain.ConsentType;
import com.firstory.user.domain.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    List<UserConsent> findAllByUserId(Long userId);

    Optional<UserConsent> findTopByUserIdAndConsentTypeOrderByIdDesc(Long userId, ConsentType consentType);
}
