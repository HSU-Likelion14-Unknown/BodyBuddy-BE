package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.meal.entity.MealItem;
import com.centerton.bodybuddy.domain.meal.entity.MealItemSource;
import com.centerton.bodybuddy.domain.meal.entity.NutritionBasis;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MealItemRes {
    private String mealItemId;
    private String foodId;
    private String foodName;
    private BigDecimal consumedAmount;
    private String consumedUnit;
    private BigDecimal confidence;
    private MealItemSource source;
    private NutritionStatus nutritionStatus;
    private NutritionBasis nutritionBasis;
    private String nutritionProvider;
    private String nutritionModel;
    private String nutritionPromptVersion;
    private BigDecimal nutritionConfidence;
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

    public static MealItemRes from(MealItem item) {
        NutritionRes nutrition = NutritionRes.from(item.getNutrition());
        MealItemResBuilder builder = MealItemRes.builder()
                .mealItemId(item.getMealItemId())
                .foodId(item.getFoodId())
                .foodName(item.getFoodName())
                .consumedAmount(item.getAmount())
                .consumedUnit(item.getAmountUnit())
                .confidence(item.getConfidence())
                .source(item.getSource())
                .nutritionStatus(nutritionStatus(item))
                .nutritionBasis(item.getNutritionBasis())
                .nutritionProvider(item.getNutritionProvider())
                .nutritionModel(item.getNutritionModel())
                .nutritionPromptVersion(item.getNutritionPromptVersion())
                .nutritionConfidence(item.getNutritionConfidence());
        if (nutrition != null) {
            builder.caloriesKcal(nutrition.getCaloriesKcal())
                    .carbohydrateG(nutrition.getCarbohydrateG())
                    .proteinG(nutrition.getProteinG())
                    .fatG(nutrition.getFatG())
                    .fiberG(nutrition.getFiberG())
                    .sodiumMg(nutrition.getSodiumMg())
                    .calciumMg(nutrition.getCalciumMg())
                    .ironMg(nutrition.getIronMg())
                    .potassiumMg(nutrition.getPotassiumMg())
                    .vitaminAMcgRae(nutrition.getVitaminAMcgRae())
                    .vitaminCMg(nutrition.getVitaminCMg());
        }
        return builder.build();
    }

    private static NutritionStatus nutritionStatus(MealItem item) {
        if (item.getNutrition() == null) {
            return NutritionStatus.UNKNOWN;
        }
        return item.getNutritionBasis() == NutritionBasis.AI_ESTIMATE
                ? NutritionStatus.ESTIMATED
                : NutritionStatus.CALCULATED;
    }
}
