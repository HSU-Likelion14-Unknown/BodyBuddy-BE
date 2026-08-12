package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.meal.entity.MealItem;
import com.centerton.bodybuddy.domain.meal.entity.MealItemSource;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MealItemRes {
    private String mealItemId;
    private String foodId;
    private String foodName;
    private BigDecimal amount;
    private String amountUnit;
    private BigDecimal confidence;
    private MealItemSource source;
    private NutritionRes nutrition;

    public static MealItemRes from(MealItem item) {
        return MealItemRes.builder()
                .mealItemId(item.getMealItemId())
                .foodId(item.getFoodId())
                .foodName(item.getFoodName())
                .amount(item.getAmount())
                .amountUnit(item.getAmountUnit())
                .confidence(item.getConfidence())
                .source(item.getSource())
                .nutrition(NutritionRes.from(item.getNutrition()))
                .build();
    }
}
