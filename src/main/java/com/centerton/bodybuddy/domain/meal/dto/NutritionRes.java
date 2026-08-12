package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class NutritionRes {
    private BigDecimal caloriesKcal;
    private BigDecimal carbohydrateG;
    private BigDecimal proteinG;
    private BigDecimal fatG;
    private BigDecimal fiberG;
    private BigDecimal sodiumMg;

    public static NutritionRes from(NutritionValues nutrition) {
        if (nutrition == null) {
            return null;
        }
        return NutritionRes.builder()
                .caloriesKcal(nutrition.getCaloriesKcal())
                .carbohydrateG(nutrition.getCarbohydrateG())
                .proteinG(nutrition.getProteinG())
                .fatG(nutrition.getFatG())
                .fiberG(nutrition.getFiberG())
                .sodiumMg(nutrition.getSodiumMg())
                .build();
    }
}
