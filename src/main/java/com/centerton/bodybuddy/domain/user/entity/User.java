package com.centerton.bodybuddy.domain.user.entity;

import com.centerton.bodybuddy.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(nullable = false, unique = true, name = "access_key_hash", length = 64)
    private String accessKeyHash;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "birth_year")
    private Integer birthYear;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allergies", columnDefinition = "json")
    private List<String> allergyCodes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "disliked_foods", columnDefinition = "json")
    private List<String> dislikedFoods;

    @Column(name = "onboarding_completed_at")
    private LocalDateTime onboardingCompletedAt;

    public void updateOnboarding(String nickname, Integer birthYear, Gender gender,
                                 List<String> allergyCodes, List<String> dislikedFoods,
                                 LocalDateTime onboardingCompletedAt) {
        this.nickname = nickname;
        this.birthYear = birthYear;
        this.gender = gender;
        this.allergyCodes = allergyCodes == null ? List.of() : List.copyOf(allergyCodes);
        this.dislikedFoods = dislikedFoods == null ? List.of() : List.copyOf(dislikedFoods);
        this.onboardingCompletedAt = onboardingCompletedAt;
    }

    public void updatePreferences(
        List<String> allergyCodes,
        List<String> dislikedFoods
) {
    this.allergyCodes = allergyCodes == null
            ? List.of()
            : List.copyOf(allergyCodes);

    this.dislikedFoods = dislikedFoods == null
            ? List.of()
            : List.copyOf(dislikedFoods);
}

}
