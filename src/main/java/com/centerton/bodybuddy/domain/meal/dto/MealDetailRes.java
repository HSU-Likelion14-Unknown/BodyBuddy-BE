package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class MealDetailRes {
    private String mealId;
    private MealStatus status;
    private OffsetDateTime eatenAt;
    private List<RecognizedItemRes> recognizedItems;
    private List<MealItemRes> items;
    private NutritionSummaryRes nutritionSummary;
    private Object recommendation;
}
