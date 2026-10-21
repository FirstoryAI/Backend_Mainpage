package com.firstory.reading.repository;

import com.firstory.reading.domain.DialogueAction;
import com.firstory.reading.domain.DialogueActionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DialogueActionRepository extends JpaRepository<DialogueAction, Long> {

    // North Star 집계용
    long countByAction(DialogueActionType action);
}
