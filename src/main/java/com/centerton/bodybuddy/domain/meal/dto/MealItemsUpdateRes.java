package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealItemsUpdateRes {
    private String mealId;
    private MealStatus status;
    private NutritionSummaryRes nutritionSummary;
}
