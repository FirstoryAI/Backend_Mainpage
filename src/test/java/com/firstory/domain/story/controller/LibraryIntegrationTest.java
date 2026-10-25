package com.firstory.domain.story.controller;

import com.firstory.domain.generation.repository.GenerationRunRepository;
import com.firstory.domain.generation.service.GenerationPoller;
import com.firstory.domain.generation.repository.StoryVersionRepository;
import com.firstory.domain.story.entity.StoryStatus;
import com.firstory.domain.story.repository.StoryInputRepository;
import com.firstory.domain.story.repository.StoryRepository;
import com.firstory.domain.user.repository.UserRepository;
import com.firstory.global.ai.FakeAiClient;
import com.firstory.support.Browser;
import com.firstory.support.IntegrationTest;
import com.firstory.support.StoryFlow;
import com.firstory.support.StoryStates;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서재(LIB-01)와 홈(HOME-01). 상태가 다른 동화는 StoryStates로 DB에 바로 만든다.
 */
class LibraryIntegrationTest extends IntegrationTest {

    @Autowired
    TransactionTemplate tx;

    @Autowired
    UserRepository userRepository;

    @Autowired
    StoryRepository storyRepository;

    @Autowired
    StoryInputRepository storyInputRepository;

    @Autowired
    GenerationRunRepository runRepository;

    @Autowired
    StoryVersionRepository versionRepository;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    GenerationPoller poller;

    StoryStates states;
    Browser parent;
    Long userId;

    @BeforeEach
    void setUp() throws Exception {
        states = new StoryStates(tx, userRepository, storyRepository, storyInputRepository, runRepository,
                versionRepository);
        parent = signUp(newEmail(), newPhone());
        userId = userId(parent);
    }

    @Test
    void 서재_필터별로_나누고_BLOCKED는_어디에도_없다() throws Exception {
        long draft = create(StoryStatus.DRAFT, false);
        long generating = create(StoryStatus.GENERATING_IMAGES, false);
        long reviewing = create(StoryStatus.REVIEWING, false);
        long unread = create(StoryStatus.COMPLETED, false);
        long read = create(StoryStatus.COMPLETED, true);
        long failed = create(StoryStatus.FAILED, false);
        long blocked = create(StoryStatus.BLOCKED, false);

        parent.get("/api/stories")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.items[*].id", not(hasItem((int) blocked))));
        expectFilter("CREATING", draft, generating);
        expectFilter("REVIEWING", reviewing);
        expectFilter("UNREAD", unread);
        expectFilter("READ", read);
        expectFilter("FAILED", failed);

        parent.get("/api/stories?filter=CREATING")
                .andExpect(jsonPath("$.items[*].label", contains("만드는 중", "만들던 이야기")));
        parent.get("/api/stories?filter=REVIEWING").andExpect(jsonPath("$.items[0].label").value("확인을 기다려요"));
        parent.get("/api/stories?filter=UNREAD").andExpect(jsonPath("$.items[0].label").value("아직 안 읽었어요"));
        parent.get("/api/stories?filter=READ").andExpect(jsonPath("$.items[0].label").value("함께 읽었어요"));
        parent.get("/api/stories?filter=FAILED").andExpect(jsonPath("$.items[0].label").value("만들지 못했어요"));
    }

    @Test
    void 서재_카드는_버전이_있어야_제목과_표지가_있다() throws Exception {
        long draft = create(StoryStatus.DRAFT, false);
        long completed = create(StoryStatus.COMPLETED, false);
        long versionId = jdbc.queryForObject("SELECT current_version_id FROM stories WHERE id = ?", Long.class,
                completed);

        parent.get("/api/stories?filter=UNREAD")
                .andExpect(jsonPath("$.items[0].id").value(completed))
                .andExpect(jsonPath("$.items[0].title").value("동화 " + completed))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].coverUrl")
                        .value("/api/stories/" + completed + "/versions/" + versionId + "/character-sheet"))
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].updatedAt").isNotEmpty());
        parent.get("/api/stories?filter=CREATING")
                .andExpect(jsonPath("$.items[0].id").value(draft))
                .andExpect(jsonPath("$.items[0].title").value(nullValue()))
                .andExpect(jsonPath("$.items[0].coverUrl").value(nullValue()))
                .andExpect(jsonPath("$.items[0].childDisplayName").value(nullValue()));
    }

    @Test
    void 서재는_최근_수정_순으로_나눠_받는다() throws Exception {
        long first = create(StoryStatus.DRAFT, false);
        advance(Duration.ofMinutes(1));
        long second = create(StoryStatus.DRAFT, false);
        advance(Duration.ofMinutes(1));
        long third = create(StoryStatus.DRAFT, false);

        parent.get("/api/stories?size=2")
                .andExpect(jsonPath("$.items[*].id", contains((int) third, (int) second)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));
        parent.get("/api/stories?size=2&page=1")
                .andExpect(jsonPath("$.items[*].id", contains((int) first)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 서재_쪽_크기와_필터를_검사한다() throws Exception {
        parent.get("/api/stories?size=51").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        parent.get("/api/stories?size=0").andExpect(status().isBadRequest());
        parent.get("/api/stories?page=-1").andExpect(status().isBadRequest());
        parent.get("/api/stories?filter=BLOCKED").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        parent.get("/api/stories?size=50").andExpect(status().isOk());
    }

    @Test
    void 서재는_다른_회원의_동화를_보여주지_않는다() throws Exception {
        create(StoryStatus.COMPLETED, false);
        Browser other = signUp(newEmail(), newPhone());

        other.get("/api/stories").andExpect(jsonPath("$.totalElements").value(0));
        other.get("/api/home").andExpect(jsonPath("$.counts.unread").value(0));
    }

    @Test
    void 서재_쿼리_수는_목록_크기와_무관하다() throws Exception {
        for (int i = 0; i < 3; i++) {
            withSavedChild(create(StoryStatus.COMPLETED, false));
        }
        // 쪽이 가득 차야 개수 쿼리도 나간다. 두 경우 모두 가득 찬 쪽으로 비교한다
        long small = queriesFor("/api/stories?size=3");

        for (int i = 0; i < 17; i++) {
            withSavedChild(create(StoryStatus.COMPLETED, i % 2 == 0));
        }
        parent.get("/api/stories").andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.items[0].childDisplayName").isNotEmpty());
        long large = queriesFor("/api/stories?size=20");

        assertThat(large).isEqualTo(small);
        assertThat(large).as("목록 1번 + 개수 1번").isEqualTo(2);
    }

    @Test
    void 홈은_진행_중_3개_최근_완성_3개와_개수() throws Exception {
        create(StoryStatus.DRAFT, false);
        create(StoryStatus.CONFIRMING, false);
        create(StoryStatus.GENERATING_TEXT, false);
        create(StoryStatus.REVIEWING, false);
        create(StoryStatus.FAILED, false);
        long oldest = create(StoryStatus.COMPLETED, false);
        advance(Duration.ofMinutes(1));
        long older = create(StoryStatus.COMPLETED, true);
        advance(Duration.ofMinutes(1));
        long newer = create(StoryStatus.COMPLETED, true);
        advance(Duration.ofMinutes(1));
        long newest = create(StoryStatus.COMPLETED, false);
        create(StoryStatus.BLOCKED, false);

        parent.get("/api/home")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingSeen").value(false))
                .andExpect(jsonPath("$.inProgress", hasSize(3)))
                .andExpect(jsonPath("$.inProgress[*].status", everyIn("DRAFT", "CONFIRMING", "GENERATING_TEXT",
                        "REVIEWING", "FAILED")))
                .andExpect(jsonPath("$.recent[*].id", contains((int) newest, (int) newer, (int) older)))
                .andExpect(jsonPath("$.counts.creating").value(3))
                .andExpect(jsonPath("$.counts.reviewing").value(1))
                .andExpect(jsonPath("$.counts.unread").value(2))
                .andExpect(jsonPath("$.counts.read").value(2))
                .andExpect(jsonPath("$.counts.failed").value(1))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());
        assertThat(oldest).isPositive();
    }

    @Test
    void 온보딩을_봤으면_홈에_표시된다() throws Exception {
        jdbc.update("UPDATE users SET onboarding_seen_at = now() WHERE id = ?", userId);

        parent.get("/api/home").andExpect(jsonPath("$.onboardingSeen").value(true))
                .andExpect(jsonPath("$.inProgress", hasSize(0)))
                .andExpect(jsonPath("$.recent", hasSize(0)));
    }

    @Test
    void 상세는_읽은_횟수와_마지막_읽은_시각을_보여준다() throws Exception {
        long id = create(StoryStatus.COMPLETED, true);
        long versionId = jdbc.queryForObject("SELECT current_version_id FROM stories WHERE id = ?", Long.class, id);
        jdbc.update("""
                INSERT INTO reading_sessions (story_id, version_id, last_page, started_at, completed_at, created_at, updated_at)
                VALUES (?, ?, 10, '2026-10-01T00:00:00Z', '2026-10-01T00:10:00Z', now(), now()),
                       (?, ?, 10, '2026-10-02T00:00:00Z', '2026-10-02T00:10:00Z', now(), now()),
                       (?, ?, 3, '2026-10-03T00:00:00Z', NULL, now(), now())""",
                id, versionId, id, versionId, id, versionId);

        parent.get("/api/stories/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("동화 " + id))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.label").value("함께 읽었어요"))
                .andExpect(jsonPath("$.coverUrl").isNotEmpty())
                .andExpect(jsonPath("$.pageCount").value(10))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.readCount").value(2))
                .andExpect(jsonPath("$.lastReadAt").value("2026-10-03T00:00:00Z"))
                .andExpect(jsonPath("$.canRead").value(true));
    }

    @Test
    void 상세는_입력한_상황과_요약을_보여준다() throws Exception {
        long id = StoryFlow.completed(parent, poller);

        parent.get("/api/stories/" + id)
                .andExpect(jsonPath("$.situation.category").value("FRIEND_CONFLICT"))
                .andExpect(jsonPath("$.situation.categoryLabel").value("친구와 다툼"))
                .andExpect(jsonPath("$.situation.timing").value("PAST"))
                .andExpect(jsonPath("$.summary").value(FakeAiClient.SUMMARY))
                .andExpect(jsonPath("$.childDisplayName").value("아이 A"))
                .andExpect(jsonPath("$.readCount").value(0))
                .andExpect(jsonPath("$.lastReadAt").value(nullValue()));
    }

    @Test
    void 확정_전이나_차단된_동화는_읽을_수_없고_차단도_상태_그대로_보인다() throws Exception {
        long reviewing = create(StoryStatus.REVIEWING, false);
        long blocked = create(StoryStatus.BLOCKED, false);

        parent.get("/api/stories/" + reviewing).andExpect(jsonPath("$.canRead").value(false));
        parent.get("/api/stories/" + blocked)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.label").value("만들 수 없는 이야기"))
                .andExpect(jsonPath("$.title").value(nullValue()))
                .andExpect(jsonPath("$.pageCount").value(nullValue()))
                .andExpect(jsonPath("$.canRead").value(false));
    }

    @Test
    void 다른_회원의_동화_상세는_404() throws Exception {
        long id = create(StoryStatus.COMPLETED, false);
        Browser other = signUp(newEmail(), newPhone());

        other.get("/api/stories/" + id).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORY_NOT_FOUND"));
    }

    @Test
    void 완성된_동화를_지우면_목록_상세_그림이_모두_404() throws Exception {
        long id = StoryFlow.completed(parent, poller);
        long versionId = jdbc.queryForObject("SELECT current_version_id FROM stories WHERE id = ?", Long.class, id);
        String image = "/api/stories/" + id + "/versions/" + versionId + "/pages/1/image";
        parent.get(image).andExpect(status().isOk());

        parent.delete("/api/stories/" + id).andExpect(status().isNoContent());

        parent.get("/api/stories").andExpect(jsonPath("$.totalElements").value(0));
        parent.get("/api/stories/" + id).andExpect(status().isNotFound());
        parent.get(image).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORY_NOT_FOUND"));
        parent.get("/api/stories/" + id + "/guide").andExpect(status().isNotFound());
    }

    @Test
    void 확인_중_실패_차단된_동화도_지울_수_있다() throws Exception {
        for (StoryStatus status : new StoryStatus[]{StoryStatus.REVIEWING, StoryStatus.FAILED, StoryStatus.BLOCKED,
                StoryStatus.COMPLETED}) {
            long id = create(status, false);
            parent.delete("/api/stories/" + id).andExpect(status().isNoContent());
        }
    }

    @Test
    void 생성_중에는_지울_수_없다() throws Exception {
        for (StoryStatus status : new StoryStatus[]{StoryStatus.REQUESTED, StoryStatus.GENERATING_TEXT,
                StoryStatus.GENERATING_IMAGES}) {
            long id = create(status, false);
            parent.delete("/api/stories/" + id).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        }
    }

    private org.hamcrest.Matcher<Iterable<? extends String>> everyIn(String... statuses) {
        return org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.in(statuses));
    }

    private void expectFilter(String filter, long... ids) throws Exception {
        Integer[] expected = java.util.Arrays.stream(ids).mapToObj(id -> (int) id).toArray(Integer[]::new);
        parent.get("/api/stories?filter=" + filter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(ids.length))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(expected)));
    }

    private long create(StoryStatus status, boolean read) {
        return states.create(userId, status, read, clock.instant());
    }

    // 아이 프로필을 저장해 연결한다 (카드의 childDisplayName이 아이를 읽게 해서 N+1을 드러낸다)
    private void withSavedChild(long storyId) {
        Long childId = jdbc.queryForObject("""
                INSERT INTO children (user_id, display_name, birth_ym, gender, interest_codes, custom_interests,
                                      created_at, updated_at)
                VALUES (?, ?, '2021-03', 'FEMALE', '[]', '[]', now(), now()) RETURNING id""", Long.class,
                userId, "아이 " + storyId);
        jdbc.update("UPDATE stories SET child_id = ? WHERE id = ?", childId, storyId);
    }

    private long queriesFor(String url) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            parent.get(url).andExpect(status().isOk());
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }
}
