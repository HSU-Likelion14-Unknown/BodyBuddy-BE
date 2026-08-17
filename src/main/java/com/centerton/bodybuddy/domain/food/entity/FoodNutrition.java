package com.centerton.bodybuddy.domain.food.entity;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "food_nutritions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FoodNutrition {

    @Id
    @Column(name = "food_id", length = 36)
    private String foodId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "food_id")
    private Food food;

    @Column(name = "reference_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal referenceAmount;

    @Column(name = "reference_unit", nullable = false, length = 20)
    private String referenceUnit;

    @Embedded
    private NutritionValues nutrition;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
