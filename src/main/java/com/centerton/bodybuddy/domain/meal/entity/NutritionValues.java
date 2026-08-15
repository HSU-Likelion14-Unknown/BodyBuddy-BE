package com.centerton.bodybuddy.domain.meal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionValues {

    @Column(name = "calories_kcal", precision = 12, scale = 2)
    private BigDecimal caloriesKcal;

    @Column(name = "carbohydrate_g", precision = 12, scale = 2)
    private BigDecimal carbohydrateG;

    @Column(name = "protein_g", precision = 12, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "fat_g", precision = 12, scale = 2)
    private BigDecimal fatG;

    @Column(name = "fiber_g", precision = 12, scale = 2)
    private BigDecimal fiberG;

    @Column(name = "sodium_mg", precision = 12, scale = 2)
    private BigDecimal sodiumMg;
}
