package com.centerton.bodybuddy.domain.user.entity;

import com.centerton.bodybuddy.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(nullable = false, unique = true, name = "access_key_hash", length = 64)
    private String accessKeyHash;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "allergies", length = 500)
    private String allergies;

    @Column(name = "disliked_foods", length = 500)
    private String dislikedFoods;

    @Column(name = "onboarding_completed_at")
    private LocalDateTime onboardingCompletedAt;

    public void updateOnboarding(String nickname, Integer birthYear, Gender gender,
                                 String allergies, String dislikedFoods,
                                 LocalDateTime onboardingCompletedAt) {
        this.nickname = nickname;
        this.birthYear = birthYear;
        this.gender = gender;
        this.allergies = allergies;
        this.dislikedFoods = dislikedFoods;
        this.onboardingCompletedAt = onboardingCompletedAt;
    }
}