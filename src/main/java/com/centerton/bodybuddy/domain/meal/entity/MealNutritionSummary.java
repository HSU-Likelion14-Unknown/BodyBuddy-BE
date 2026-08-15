package com.centerton.bodybuddy.domain.meal.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "meal_nutrition_summaries")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MealNutritionSummary {

    @Id
    @Column(name = "meal_id", length = 36)
    private String mealId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "meal_id")
    private Meal meal;

    @Embedded
    private NutritionValues nutrition;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false, length = 24)
    private NutritionBasis basis;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
