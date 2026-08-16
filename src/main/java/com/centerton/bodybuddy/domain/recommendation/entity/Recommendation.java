package com.centerton.bodybuddy.domain.recommendation.entity;

import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recommendations")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Recommendation extends BaseEntity {

    @Id
    @Column(name = "recommendation_id", length = 36)
    private String recommendationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    @Column(name = "recommendation_date", nullable = false)
    private LocalDate recommendationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private RecommendationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_nutrient", length = 40)
    private TargetNutrient targetNutrient;

    @Enumerated(EnumType.STRING)
    @Column(name = "no_recommendation_reason", length = 80)
    private NoRecommendationReason noRecommendationReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "daily_nutrition", nullable = false, columnDefinition = "json")
    private NutritionValues dailyNutrition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nutrient_gap", nullable = false, columnDefinition = "json")
    private NutritionValues nutrientGap;

    public static Recommendation created(User user, Meal meal, LocalDate date,
                                         TargetNutrient targetNutrient,
                                         NutritionValues dailyNutrition,
                                         NutritionValues nutrientGap) {
        return baseBuilder(user, meal, date, dailyNutrition, nutrientGap)
                .status(RecommendationStatus.CREATED)
                .targetNutrient(targetNutrient)
                .build();
    }

    public static Recommendation noCandidate(User user, Meal meal, LocalDate date,
                                             TargetNutrient targetNutrient,
                                             NoRecommendationReason reason,
                                             NutritionValues dailyNutrition,
                                             NutritionValues nutrientGap) {
        return baseBuilder(user, meal, date, dailyNutrition, nutrientGap)
                .status(RecommendationStatus.NO_CANDIDATE)
                .targetNutrient(targetNutrient)
                .noRecommendationReason(reason)
                .build();
    }

    private static RecommendationBuilder baseBuilder(
            User user,
            Meal meal,
            LocalDate date,
            NutritionValues dailyNutrition,
            NutritionValues nutrientGap
    ) {
        return Recommendation.builder()
                .recommendationId(UUID.randomUUID().toString())
                .user(user)
                .meal(meal)
                .recommendationDate(date)
                .dailyNutrition(dailyNutrition)
                .nutrientGap(nutrientGap);
    }

    public void select() {
        this.status = RecommendationStatus.SELECTED;
    }

    public void skip() {
        this.status = RecommendationStatus.SKIPPED;
    }
}
