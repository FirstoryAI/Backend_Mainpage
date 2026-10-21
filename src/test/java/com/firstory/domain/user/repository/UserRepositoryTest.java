package com.firstory.domain.user.repository;

import com.firstory.domain.user.entity.User;
import com.firstory.support.RepositoryTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static com.firstory.support.Fixtures.NOW;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryTest extends RepositoryTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager em;

    @Test
    void 이메일은_소문자로_저장하고_같은_이메일은_두_번_가입할_수_없다() {
        userRepository.saveAndFlush(user(" Mom@Example.com "));

        assertThat(userRepository.existsByEmail("mom@example.com")).isTrue();
        assertThatThrownBy(() -> userRepository.saveAndFlush(user("mom@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 탈퇴한_회원의_이메일로는_다시_가입할_수_있다() {
        User first = userRepository.saveAndFlush(user("dad@example.com"));
        first.withdraw(NOW);
        em.flush();
        em.clear();

        assertThat(userRepository.existsByEmail("dad@example.com")).isFalse();

        User second = userRepository.saveAndFlush(user("dad@example.com"));
        assertThat(userRepository.findByEmail("dad@example.com")).get()
                .extracting(User::getId).isEqualTo(second.getId());
    }
}
