package com.firstory.child.repository;

import com.firstory.child.domain.Child;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChildRepository extends JpaRepository<Child, Long> {

    List<Child> findAllByUserIdOrderByIdAsc(Long userId);

    Optional<Child> findByIdAndUserId(Long id, Long userId);

    @Query("select c.displayName from Child c where c.user.id = :userId")
    List<String> findDisplayNamesByUserId(@Param("userId") Long userId);
}
