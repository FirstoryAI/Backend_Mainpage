package com.firstory.domain.reading.repository;

import com.firstory.domain.reading.entity.DialogueAction;
import com.firstory.domain.reading.entity.DialogueActionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DialogueActionRepository extends JpaRepository<DialogueAction, Long> {

    // North Star 집계용
    long countByAction(DialogueActionType action);
}
