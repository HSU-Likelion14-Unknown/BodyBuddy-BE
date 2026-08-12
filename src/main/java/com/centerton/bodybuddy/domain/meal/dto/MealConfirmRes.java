package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MealConfirmRes {
    private String mealId;
    private MealStatus status;
    private List<MealItemRes> items;
    private NutritionSummaryRes nutritionSummary;
}
