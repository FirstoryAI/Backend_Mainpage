package com.firstory.child.domain;

import com.firstory.global.converter.YearMonthStringConverter;
import com.firstory.global.entity.BaseTimeEntity;
import com.firstory.user.domain.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Getter
@Entity
@Table(name = "children")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Child extends BaseTimeEntity {

    public static final int DISPLAY_NAME_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 부모에게만 보이는 호칭. AI 서비스로 보내지 않는다
    @Column(name = "display_name", nullable = false, length = 30)
    private String displayName;

    @Convert(converter = YearMonthStringConverter.class)
    @Column(name = "birth_ym", nullable = false, length = 7)
    private YearMonth birthYm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "child_traits", joinColumns = @JoinColumn(name = "child_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "trait_code", nullable = false, length = 30)
    private Set<Trait> traits = EnumSet.noneOf(Trait.class);

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> interests = new ArrayList<>();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Child(User user, String displayName, YearMonth birthYm, Gender gender,
                  Collection<Trait> traits, List<String> interests) {
        this.user = Objects.requireNonNull(user);
        this.displayName = validateDisplayName(displayName);
        applyProfile(birthYm, gender, traits, interests);
    }

    public static Child save(User user, String displayName, YearMonth birthYm, Gender gender,
                             Collection<Trait> traits, List<String> interests) {
        return new Child(user, displayName, birthYm, gender, traits, interests);
    }

    public void updateProfile(YearMonth birthYm, Gender gender, Collection<Trait> traits, List<String> interests) {
        applyProfile(birthYm, gender, traits, interests);
    }

    public void rename(String displayName) {
        this.displayName = validateDisplayName(displayName);
    }

    public void delete(Instant now) {
        if (deletedAt == null) {
            deletedAt = now;
        }
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId() != null && user.getId().equals(userId);
    }

    public int ageMonthsAt(LocalDate date) {
        return (int) ChronoUnit.MONTHS.between(birthYm, YearMonth.from(date));
    }

    private void applyProfile(YearMonth birthYm, Gender gender, Collection<Trait> traits, List<String> interests) {
        this.birthYm = Objects.requireNonNull(birthYm);
        this.gender = Objects.requireNonNull(gender);
        // 영속 컬렉션은 교체하지 않고 내용만 바꾼다
        this.traits.clear();
        if (traits != null) {
            this.traits.addAll(traits);
        }
        this.interests = Interests.normalize(interests);
    }

    private static String validateDisplayName(String displayName) {
        String stripped = displayName == null ? "" : displayName.strip();
        int length = stripped.codePointCount(0, stripped.length());
        if (length < 1 || length > DISPLAY_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("아이 호칭은 1~30자여야 해요.");
        }
        return stripped;
    }
}
