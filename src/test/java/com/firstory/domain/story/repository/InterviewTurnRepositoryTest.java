package com.firstory.domain.story.repository;

import com.firstory.domain.story.entity.InterviewTurn;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.support.RepositoryTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.firstory.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewTurnRepositoryTest extends RepositoryTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    StoryRepository storyRepository;

    @Autowired
    InterviewTurnRepository interviewTurnRepository;

    @Autowired
    EntityManager em;

    @Test
    void DB도_15번째_차례까지_받고_16번째는_거부한다() {
        Story story = storyRepository.save(story(userRepository.save(user())));

        interviewTurnRepository.saveAndFlush(InterviewTurn.ask(story, InterviewTurn.MAX_TURNS, "15번째 질문"));
        assertThat(interviewTurnRepository.count()).isEqualTo(1);

        assertThatThrownBy(() -> em.createNativeQuery("""
                        INSERT INTO interview_turns (story_id, turn_no, question, created_at, updated_at)
                        VALUES (:storyId, 16, '16번째 질문', now(), now())""")
                .setParameter("storyId", story.getId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }
}
