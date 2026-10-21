package com.firstory.domain.child.entity;

import com.firstory.domain.user.entity.User;
import com.firstory.global.converter.YearMonthStringConverter;
import com.firstory.global.entity.BaseTimeEntity;
import com.firstory.global.entity.CustomText;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
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
    public static final int CUSTOM_TRAIT_MAX_LENGTH = 20;

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

    // traits에 OTHER가 있을 때만 값이 있다
    @Column(name = "custom_trait", length = 20)
    private String customTrait;

    // InterestCode 선언 순서
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interest_codes", nullable = false, columnDefinition = "jsonb")
    private List<InterestCode> interestCodes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_interests", nullable = false, columnDefinition = "jsonb")
    private List<String> customInterests = new ArrayList<>();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Child(User user, String displayName, YearMonth birthYm, Gender gender,
                  Collection<Trait> traits, String customTrait, Interests interests) {
        this.user = Objects.requireNonNull(user);
        this.displayName = validateDisplayName(displayName);
        applyProfile(birthYm, gender, traits, customTrait, interests);
    }

    public static Child save(User user, String displayName, YearMonth birthYm, Gender gender,
                             Collection<Trait> traits, String customTrait, Interests interests) {
        return new Child(user, displayName, birthYm, gender, traits, customTrait, interests);
    }

    public void updateProfile(YearMonth birthYm, Gender gender, Collection<Trait> traits, String customTrait,
                              Interests interests) {
        applyProfile(birthYm, gender, traits, customTrait, interests);
    }

    /**
     * 저장된 값과 같은지. 같으면 갱신하지 않아 child_traits 행을 지우고 다시 넣지 않게 한다.
     */
    public boolean hasProfile(YearMonth birthYm, Gender gender, Collection<Trait> traits, String customTrait,
                              Interests interests) {
        Set<Trait> traitSet = toTraitSet(traits);
        Interests normalized = Interests.normalize(interests.codes(), interests.customs());
        return this.birthYm.equals(birthYm)
                && this.gender == gender
                && this.traits.equals(traitSet)
                && Objects.equals(this.customTrait, customTraitOf(traitSet, customTrait))
                && this.interestCodes.equals(normalized.codes())
                && this.customInterests.equals(normalized.customs());
    }

    public Interests getInterests() {
        return new Interests(interestCodes, customInterests);
    }

    /**
     * 성향 직접 입력. OTHER를 골랐으면 필수(20자까지), 고르지 않았으면 버린다.
     */
    public static String customTraitOf(Collection<Trait> traits, String customTrait) {
        return CustomText.resolve(traits != null && traits.contains(Trait.OTHER), customTrait,
                CUSTOM_TRAIT_MAX_LENGTH);
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

    private void applyProfile(YearMonth birthYm, Gender gender, Collection<Trait> traits, String customTrait,
                              Interests interests) {
        Set<Trait> traitSet = toTraitSet(traits);
        Interests normalized = Interests.normalize(interests.codes(), interests.customs());
        this.customTrait = customTraitOf(traitSet, customTrait);
        this.birthYm = Objects.requireNonNull(birthYm);
        this.gender = Objects.requireNonNull(gender);
        // 영속 컬렉션은 교체하지 않고 내용만 바꾼다
        this.traits.clear();
        this.traits.addAll(traitSet);
        this.interestCodes = new ArrayList<>(normalized.codes());
        this.customInterests = new ArrayList<>(normalized.customs());
    }

    private static Set<Trait> toTraitSet(Collection<Trait> traits) {
        Set<Trait> set = EnumSet.noneOf(Trait.class);
        if (traits != null) {
            traits.stream().filter(Objects::nonNull).forEach(set::add);
        }
        return set;
    }

    private static String validateDisplayName(String displayName) {
        String stripped = displayName == null ? "" : displayName.strip();
        int length = stripped.codePointCount(0, stripped.length());
        if (length < 1 || length > DISPLAY_NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_CHILD_NAME);
        }
        return stripped;
    }
}
