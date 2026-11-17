package com.firstory.domain.credit.service;

import com.firstory.domain.credit.entity.CreditSource;
import com.firstory.domain.credit.entity.PreContact;
import com.firstory.domain.credit.entity.StoryCredit;
import com.firstory.domain.credit.entity.StoryCreditUse;
import com.firstory.domain.credit.repository.PreContactRepository;
import com.firstory.domain.credit.repository.StoryCreditRepository;
import com.firstory.domain.credit.repository.StoryCreditUseRepository;
import com.firstory.domain.story.entity.Story;
import com.firstory.domain.user.entity.User;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 동화 이용권. 동화 한 편을 AI로 처음 만들 때(생성 확정) 1회를 쓴다. 다시 만들기·다시 시도는 쓰지 않는다.
 * 개발용 이메일(firstory.beta.unlimited-emails)은 이용권 없이 무제한이다.
 * firstory.beta.credits-enabled가 false면 확인하지 않는다 (test 프로필).
 */
@Service
@RequiredArgsConstructor
public class CreditService {

    private final StoryCreditRepository creditRepository;
    private final StoryCreditUseRepository useRepository;
    private final PreContactRepository preContactRepository;
    private final UserRepository userRepository;
    private final FirstoryProperties properties;
    private final Clock clock;

    /**
     * 남은 횟수. 무제한이면 remaining은 null.
     */
    public record CreditSummary(boolean unlimited, Integer remaining) {
    }

    @Transactional(readOnly = true)
    public CreditSummary summary(Long userId) {
        User user = findUser(userId);
        if (isUnlimited(user)) {
            return new CreditSummary(true, null);
        }
        return new CreditSummary(false, remaining(creditRepository.findByUserIdOrderByIdAsc(userId)));
    }

    /**
     * 동화 만들기를 시작하기 전에 본다 (POST /api/stories). 남은 횟수가 없으면 STORY_LIMIT_REACHED.
     * 쓰지는 않는다. 쓰는 것은 생성 확정(reserve).
     */
    @Transactional
    public void requireAvailable(Long userId) {
        User user = findUser(userId);
        if (isUnlimited(user)) {
            return;
        }
        syncPreContact(user);
        if (remaining(creditRepository.findByUserIdOrderByIdAsc(userId)) <= 0) {
            throw new BusinessException(ErrorCode.STORY_LIMIT_REACHED);
        }
    }

    /**
     * 생성 확정 ①(트랜잭션 안)에서 AI를 부르기 전에 1회를 쓴다. 같은 회원의 이용권 행을 잠가 동시 확정이 한도를 넘지 않게 한다.
     * 곧 끝나는 이용권부터 쓴다. AI가 받지 못하면 release로 되돌린다.
     */
    @Transactional
    public void reserve(Story story) {
        User user = story.getUser();
        if (isUnlimited(user) || useRepository.existsByStoryId(story.getId())) {
            return;
        }
        syncPreContact(user);
        Instant now = clock.instant();
        List<StoryCredit> credits = creditRepository.findByUserIdForUpdate(user.getId());
        Map<Long, Long> used = usedCounts(credits);
        StoryCredit credit = credits.stream()
                .filter(c -> c.isActive(now) && c.getAmount() > used.getOrDefault(c.getId(), 0L))
                .min(Comparator.comparing((StoryCredit c) -> c.getExpiresAt() == null ? Instant.MAX : c.getExpiresAt())
                        .thenComparing(StoryCredit::getId))
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_LIMIT_REACHED));
        useRepository.save(StoryCreditUse.of(credit, story));
    }

    /**
     * AI가 생성을 받지 못했을 때 쓴 1회를 되돌린다.
     */
    @Transactional
    public void release(Long storyId) {
        useRepository.deleteByStoryId(storyId);
    }

    /**
     * 사전 연락 이메일로 이미 가입한 회원에게 사전 연락 이용권을 준다 (한 번만).
     * 무료 이용권은 한 종류만이라, 아직 안 쓴 일반 무료 1회는 없앤다.
     */
    @Transactional
    public void syncPreContact(Long userId) {
        syncPreContact(findUser(userId));
    }

    private void syncPreContact(User user) {
        Optional<PreContact> contact = preContactRepository.findByEmailForUpdate(user.getEmail())
                .filter(found -> !found.isGranted());
        if (contact.isEmpty()) {
            return;
        }
        List<StoryCredit> credits = creditRepository.findByUserIdForUpdate(user.getId());
        Map<Long, Long> used = usedCounts(credits);
        credits.stream().filter(c -> c.getSource() == CreditSource.SIGNUP_FREE)
                .forEach(c -> c.closeAt(used.getOrDefault(c.getId(), 0L)));
        creditRepository.save(StoryCredit.grant(user, CreditSource.PRE_CONTACT, contact.get().getCreditAmount(),
                contact.get().getCreditExpiresAt(), null));
        contact.get().markGranted(user, clock.instant());
    }

    private boolean isUnlimited(User user) {
        return !properties.beta().creditsEnabled() || properties.beta().isUnlimited(user.getEmail());
    }

    private int remaining(List<StoryCredit> credits) {
        Instant now = clock.instant();
        Map<Long, Long> used = usedCounts(credits);
        return (int) credits.stream().filter(c -> c.isActive(now))
                .mapToLong(c -> Math.max(0, c.getAmount() - used.getOrDefault(c.getId(), 0L)))
                .sum();
    }

    private Map<Long, Long> usedCounts(List<StoryCredit> credits) {
        Map<Long, Long> used = new HashMap<>();
        if (credits.isEmpty()) {
            return used;
        }
        for (Object[] row : useRepository.countByCreditIds(credits.stream().map(StoryCredit::getId).toList())) {
            used.put((Long) row[0], (Long) row[1]);
        }
        return used;
    }

    // 탈퇴했거나 세션이 남아 있는 사이 지워진 회원이면 다시 로그인하게 한다
    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
