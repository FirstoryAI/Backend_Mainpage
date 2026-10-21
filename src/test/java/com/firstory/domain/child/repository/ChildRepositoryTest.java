package com.firstory.domain.child.repository;

import com.firstory.domain.child.entity.Child;
import com.firstory.domain.child.entity.ChildLabelPolicy;
import com.firstory.domain.child.entity.Trait;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.support.RepositoryTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.YearMonth;

import static com.firstory.support.Fixtures.NOW;
import static com.firstory.support.Fixtures.child;
import static com.firstory.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

class ChildRepositoryTest extends RepositoryTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    ChildRepository childRepository;

    @Autowired
    EntityManager em;

    @Test
    void 성향과_관심사가_그대로_돌아온다() {
        User user = userRepository.save(user());
        Child saved = childRepository.save(child(user, "아이 A"));
        em.flush();
        em.clear();

        Child found = childRepository.findByIdAndUserId(saved.getId(), user.getId()).orElseThrow();

        assertThat(found.getTraits()).containsExactlyInAnyOrder(Trait.SHY, Trait.CURIOUS);
        assertThat(found.getInterests()).containsExactly("공룡", "기차");
        assertThat(found.getBirthYm()).isEqualTo(YearMonth.of(2021, 3));
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void 다른_사람의_아이는_조회되지_않는다() {
        User owner = userRepository.save(user());
        User stranger = userRepository.save(user());
        Child saved = childRepository.save(child(owner, "아이 A"));

        assertThat(childRepository.findByIdAndUserId(saved.getId(), stranger.getId())).isEmpty();
    }

    @Test
    void 삭제된_아이는_목록과_호칭_계산에서_빠진다() {
        User user = userRepository.save(user());
        Child a = childRepository.save(child(user, "아이 A"));
        Child b = childRepository.save(child(user, "아이 B"));
        a.delete(NOW);
        em.flush();
        em.clear();

        assertThat(childRepository.findAllByUserIdOrderByIdAsc(user.getId()))
                .extracting(Child::getId).containsExactly(b.getId());
        assertThat(ChildLabelPolicy.nextLabel(childRepository.findDisplayNamesByUserId(user.getId())))
                .isEqualTo("아이 A");
    }
}
