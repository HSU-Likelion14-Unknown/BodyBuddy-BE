package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.meal.entity.MealNutritionSummary;
import com.centerton.bodybuddy.domain.meal.entity.NutritionBasis;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class NutritionSummaryRes {
    private BigDecimal caloriesKcal;
    private BigDecimal carbohydrateG;
    private BigDecimal proteinG;
    private BigDecimal fatG;
    private BigDecimal fiberG;
    private BigDecimal sodiumMg;
    private BigDecimal calciumMg;
    private BigDecimal ironMg;
    private BigDecimal potassiumMg;
    private BigDecimal vitaminAMcgRae;
    private BigDecimal vitaminCMg;
    private NutritionBasis basis;

    public static NutritionSummaryRes from(MealNutritionSummary summary) {
        if (summary == null || summary.getNutrition() == null) {
            return null;
        }
        NutritionRes nutrition = NutritionRes.from(summary.getNutrition());
        return NutritionSummaryRes.builder()
                .caloriesKcal(nutrition.getCaloriesKcal())
                .carbohydrateG(nutrition.getCarbohydrateG())
                .proteinG(nutrition.getProteinG())
                .fatG(nutrition.getFatG())
                .fiberG(nutrition.getFiberG())
                .sodiumMg(nutrition.getSodiumMg())
                .calciumMg(nutrition.getCalciumMg())
                .ironMg(nutrition.getIronMg())
                .potassiumMg(nutrition.getPotassiumMg())
                .vitaminAMcgRae(nutrition.getVitaminAMcgRae())
                .vitaminCMg(nutrition.getVitaminCMg())
                .basis(summary.getBasis())
                .build();
    }
}
